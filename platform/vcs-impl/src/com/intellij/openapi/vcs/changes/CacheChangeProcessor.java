/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentableException;
import com.intellij.openapi.util.diff.impl.DiffRequestProcessor;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.requests.ErrorDiffRequest;
import com.intellij.openapi.util.diff.requests.NoDiffRequest;
import com.intellij.openapi.util.diff.tools.util.SoftHardCacheMap;
import com.intellij.openapi.util.diff.util.CalledInBackground;
import com.intellij.openapi.util.diff.util.DiffUserDataKeys;
import com.intellij.openapi.util.diff.util.DiffUserDataKeys.ScrollToPolicy;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestPresentable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class CacheChangeProcessor extends DiffRequestProcessor {
  private static final Logger LOG = Logger.getInstance(CacheChangeProcessor.class);

  @NotNull private final Project myProject;

  @NotNull private final UserDataHolder myContextDataHolder = new UserDataHolderBase();
  @NotNull private final SoftHardCacheMap<Change, Pair<Change, DiffRequest>> myRequestCache =
    new SoftHardCacheMap<Change, Pair<Change, DiffRequest>>(5, 5);

  @Nullable private Change myCurrentChange;

  @Nullable private ProgressIndicator myProgressIndicator;
  private int myModificationStamp;

  public CacheChangeProcessor(@NotNull Project project) {
    super(project);
    myProject = project;

    init();
  }

  @Override
  protected void init() {
    super.init();
    myContextDataHolder.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
  }

  //
  // Abstract
  //

  @NotNull
  protected abstract List<Change> getSelectedChanges();

  @NotNull
  protected abstract List<Change> getAllChanges();

  protected abstract void selectChange(@NotNull Change change);

  //
  // Update
  //

  public void updateRequest(final boolean force, @Nullable final ScrollToPolicy scrollToChangePolicy) {
    if (myProgressIndicator != null) myProgressIndicator.cancel();
    myProgressIndicator = null;
    myModificationStamp++;

    final Change change = myCurrentChange;
    DiffRequest cachedRequest = loadRequestFast(change);
    if (cachedRequest != null) {
      applyRequest(cachedRequest, force, scrollToChangePolicy);
      return;
    }

    applyRequest(new ErrorDiffRequest("Loading..."), force, scrollToChangePolicy);

    myProgressIndicator = new EmptyProgressIndicator();
    final ProgressIndicator indicator = myProgressIndicator;
    final int modificationStamp = myModificationStamp;

    // TODO: delayed update in background ?
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final DiffRequest request = loadRequest(change, indicator);
        indicator.checkCanceled();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (myModificationStamp != modificationStamp) return;
            myRequestCache.put(change, Pair.create(change, request));
            applyRequest(request, force, scrollToChangePolicy);
          }
        }, ModalityState.any());
      }
    });
  }

  @Nullable
  @Contract("null -> !null")
  protected DiffRequest loadRequestFast(@Nullable Change change) {
    if (change == null) return NoDiffRequest.INSTANCE;

    Pair<Change, DiffRequest> pair = myRequestCache.get(change);
    if (pair != null) {
      Change oldChange = pair.first;
      // TODO: check if we should make a better check
      if (Comparing.equal(oldChange.getBeforeRevision(), change.getBeforeRevision()) &&
          Comparing.equal(oldChange.getAfterRevision(), change.getAfterRevision())) {
        return pair.second;
      }
    }

    return null;
  }

  @NotNull
  @CalledInBackground
  private DiffRequest loadRequest(@NotNull Change change, @NotNull ProgressIndicator indicator) {
    ChangeDiffRequestPresentable presentable = ChangeDiffRequestPresentable.create(myProject, change);
    if (presentable == null) return new ErrorDiffRequest("Can't show diff");
    try {
      return presentable.process(getContext(), indicator);
    }
    catch (ProcessCanceledException e) {
      return new ErrorDiffRequest(presentable, "Operation Canceled"); // TODO: add reload action
    }
    catch (DiffRequestPresentableException e) {
      return new ErrorDiffRequest(presentable, e);
    }
    catch (Exception e) {
      return new ErrorDiffRequest(presentable, e);
    }
  }

  //
  // Impl
  //

  @Override
  protected void onDispose() {
    super.onDispose();
    myRequestCache.clear();
  }

  @Nullable
  @Override
  public <T> T getContextUserData(@NotNull Key<T> key) {
    return myContextDataHolder.getUserData(key);
  }

  @Override
  public <T> void putContextUserData(@NotNull Key<T> key, @Nullable T value) {
    myContextDataHolder.putUserData(key, value);
  }

  //
  // Navigation
  //

  /*
   * Multiple selection:
   * - iterate inside selection
   *
   * Single selection:
   * - iterate all changes
   * - update selection after movement
   *
   * current element should always be among allChanges and selection (if they are not empty)
   */

  public void clear() {
    myCurrentChange = null;
    updateRequest();
  }

  public void refresh() {
    List<Change> selectedChanges = getSelectedChanges();

    if (selectedChanges.isEmpty()) {
      myCurrentChange = null;
      updateRequest();
      return;
    }

    if (selectedChanges.contains(myCurrentChange)) return;
    myCurrentChange = selectedChanges.get(0);
    updateRequest();
  }

  @Override
  protected boolean hasNextChange() {
    if (myCurrentChange == null) return false;

    List<Change> selectedChanges = getSelectedChanges();
    if (selectedChanges.isEmpty()) return false;

    if (selectedChanges.size() > 1) {
      int index = selectedChanges.indexOf(myCurrentChange);
      assert index != -1;
      return index < selectedChanges.size() - 1;
    }
    else {
      List<Change> allChanges = getAllChanges();
      int index = allChanges.indexOf(myCurrentChange);
      assert index != -1;
      return index < allChanges.size() - 1;
    }
  }

  @Override
  protected boolean hasPrevChange() {
    if (myCurrentChange == null) return false;

    List<Change> selectedChanges = getSelectedChanges();
    if (selectedChanges.isEmpty()) return false;

    if (selectedChanges.size() > 1) {
      int index = selectedChanges.indexOf(myCurrentChange);
      assert index != -1;
      return index > 0;
    }
    else {
      List<Change> allChanges = getAllChanges();
      int index = allChanges.indexOf(myCurrentChange);
      assert index != -1;
      return index > 0;
    }
  }

  @Override
  protected void goToNextChange(boolean fromDifferences) {
    List<Change> selectedChanges = getSelectedChanges();
    List<Change> allChanges = getAllChanges();

    if (selectedChanges.size() > 1) {
      int index = selectedChanges.indexOf(myCurrentChange);
      myCurrentChange = selectedChanges.get(index + 1);
    }
    else {
      int index = allChanges.indexOf(myCurrentChange);
      myCurrentChange = allChanges.get(index + 1);
      selectChange(myCurrentChange);
    }

    updateRequest(false, fromDifferences ? ScrollToPolicy.FIRST_CHANGE : null);
  }

  @Override
  protected void goToPrevChange(boolean fromDifferences) {
    List<Change> selectedChanges = getSelectedChanges();
    List<Change> allChanges = getAllChanges();

    if (selectedChanges.size() > 1) {
      int index = selectedChanges.indexOf(myCurrentChange);
      myCurrentChange = selectedChanges.get(index - 1);
    }
    else {
      int index = allChanges.indexOf(myCurrentChange);
      myCurrentChange = allChanges.get(index - 1);
      selectChange(myCurrentChange);
    }

    updateRequest(false, fromDifferences ? ScrollToPolicy.LAST_CHANGE : null);
  }

  @Override
  protected boolean isNavigationEnabled() {
    return getSelectedChanges().size() > 1 || getAllChanges().size() > 1;
  }
}
