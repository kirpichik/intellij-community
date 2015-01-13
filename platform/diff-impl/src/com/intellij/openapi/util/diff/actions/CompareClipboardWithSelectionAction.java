/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.util.diff.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.diff.contents.DocumentContent;
import com.intellij.openapi.util.diff.impl.DiffContentFactory;
import com.intellij.openapi.util.diff.impl.DiffRequestFactory;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.util.diff.util.DiffUserDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompareClipboardWithSelectionAction extends BaseShowDiffAction {
  @Nullable
  private static Editor getEditor(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return null;

    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor != null) return editor;

    editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor != null) return editor;

    return null;
  }

  @Override
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    Editor editor = getEditor(e);
    return editor != null;
  }

  @Nullable
  @Override
  protected DiffRequest getDiffRequest(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    Editor editor = getEditor(e);
    assert editor != null;

    DocumentContent content2 = createContent(project, editor);
    DocumentContent content1 = DiffContentFactory.createClipboardContent(content2);

    String title1 = DiffBundle.message("diff.content.clipboard.content.title");
    String title2 = createContentTitle(editor);

    String title = DiffBundle.message("diff.clipboard.vs.editor.dialog.title");

    SimpleDiffRequest request = new SimpleDiffRequest(title, content1, content2, title1, title2);
    if (editor.isViewer()) {
      request.putUserData(DiffUserDataKeys.FORCE_READ_ONLY_CONTENTS, new boolean[]{false, true});
    }
    return request;
  }

  @NotNull
  private static DocumentContent createContent(@NotNull Project project, @NotNull Editor editor) {
    DocumentContent content = DiffContentFactory.create(project, editor.getDocument());

    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      TextRange range = new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      content = new DocumentFragmentContent(project, content, range);
    }

    return content;
  }

  @NotNull
  private static String createContentTitle(@NotNull Editor editor) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    String title = file != null ? DiffRequestFactory.getVirtualFileContentTitle(file) : "Editor";

    if (editor.getSelectionModel().hasSelection()) {
      title = DiffBundle.message("diff.content.selection.from.file.content.title", title);
    }

    return title;
  }
}
