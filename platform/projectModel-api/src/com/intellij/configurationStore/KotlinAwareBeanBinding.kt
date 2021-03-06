// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.IntArrayList
import com.intellij.util.serialization.BaseBeanBinding
import com.intellij.util.serialization.PropertyAccessor
import com.intellij.util.xmlb.BeanBinding
import com.intellij.util.xmlb.SerializationFilter
import org.jdom.Element

internal class KotlinAwareBeanBinding(beanClass: Class<*>) : BeanBinding(beanClass) {
  private val beanBinding = BaseBeanBinding(beanClass)

  // only for accessor, not field
  private fun findBindingIndex(name: String): Int {
    // accessors sorted by name
    val index = ObjectUtils.binarySearch(0, myBindings.size) { index -> myBindings[index].accessor.name.compareTo(name) }
    if (index >= 0) {
      return index
    }

    for ((i, binding) in myBindings.withIndex()) {
      val accessor = binding.accessor
      if (accessor is PropertyAccessor && accessor.getterName == name) {
        return i
      }
    }

    return -1
  }

  override fun serializeInto(o: Any, element: Element?, filter: SerializationFilter?): Element? {
    return when (o) {
      is BaseState -> serializeBaseStateInto(o, element, filter)
      else -> super.serializeInto(o, element, filter)
    }
  }

  fun serializeBaseStateInto(o: BaseState, _element: Element?, filter: SerializationFilter?, excludedPropertyNames: Collection<String>? = null): Element? {
    var element = _element
    // order of bindings must be used, not order of properties
    var bindingIndices: IntArrayList? = null
    for (property in o.__getProperties()) {
      val propertyName = property.name!!

      if (property.isEqualToDefault() || (excludedPropertyNames != null && excludedPropertyNames.contains(propertyName))) {
        continue
      }

      val propertyBindingIndex = findBindingIndex(propertyName)
      if (propertyBindingIndex < 0) {
        logger<BaseState>().debug("cannot find binding for property ${propertyName}")
        continue
      }

      if (bindingIndices == null) {
        bindingIndices = IntArrayList()
      }
      bindingIndices.add(propertyBindingIndex)
    }

    if (bindingIndices != null) {
      bindingIndices.sort()
      for (i in 0 until bindingIndices.size()) {
        element = serializePropertyInto(myBindings[bindingIndices.getQuick(i)], o, element, filter, false)
      }
    }
    return element
  }

  override fun newInstance(): Any {
    return beanBinding.newInstance()
  }
}