/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.util;

import java.beans.PropertyEditor;

import org.springframework.beans.BeansException;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.expression.TypeConverter;

/**
 * @author Dave Syer
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @author Soby Chacko
 */
public class BeanFactoryTypeConverter implements TypeConverter, BeanFactoryAware {

	private static ConversionService defaultConversionService;


	private volatile SimpleTypeConverter delegate = new SimpleTypeConverter();

	private volatile boolean haveCalledDelegateGetDefaultEditor;

	private volatile ConversionService conversionService;


	public BeanFactoryTypeConverter() {
		synchronized (BeanFactoryTypeConverter.class) {
			if (defaultConversionService == null) {
				defaultConversionService = new DefaultConversionService();
			}
		}
		this.conversionService = defaultConversionService;
	}

	public BeanFactoryTypeConverter(ConversionService conversionService) {
		this.conversionService = conversionService;
	}


	public void setConversionService(ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		if (beanFactory instanceof ConfigurableBeanFactory) {
			Object typeConverter = ((ConfigurableBeanFactory) beanFactory).getTypeConverter();
			if (typeConverter instanceof SimpleTypeConverter) {
				delegate = (SimpleTypeConverter) typeConverter;
			}
		}
	}

	public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
		if (conversionService.canConvert(sourceType, targetType)) {
			return true;
		}
		if (!String.class.isAssignableFrom(sourceType) && !String.class.isAssignableFrom(targetType)) {
			// PropertyEditor cannot convert non-Strings
			return false;
		}
		if (!String.class.isAssignableFrom(sourceType)) {
			return delegate.findCustomEditor(sourceType, null) != null || this.getDefaultEditor(sourceType) != null;
		}
		return delegate.findCustomEditor(targetType, null) != null || this.getDefaultEditor(targetType) != null;
	}

	public boolean canConvert(TypeDescriptor sourceTypeDescriptor, TypeDescriptor targetTypeDescriptor) {
		if (conversionService.canConvert(sourceTypeDescriptor, targetTypeDescriptor)) {
			return true;
		}
		// TODO: what does this mean? This method is not used in SpEL so probably ignorable?
		Class<?> sourceType = sourceTypeDescriptor.getObjectType();
		Class<?> targetType = targetTypeDescriptor.getObjectType();
		return canConvert(sourceType, targetType);
	}

	public Object convertValue(Object value, TypeDescriptor sourceType, TypeDescriptor targetType) {
		// Echoes org.springframework.expression.common.ExpressionUtils.convertTypedValue()
		if ((targetType.getType() == Void.class || targetType.getType() == Void.TYPE) && value == null) {
			return null;
		}
		/*
		 *  INT-2630 Spring 3.1 now converts ALL arguments; we know we don't need to convert MessageHeaders
		 *  or MessageHistory; the MapToMap converter requires a no-arg constructor.
		 *  Also INT-2650 - don't convert large byte[]
		 *  This reverts the effective logic to Spring 3.0.
		 */
		if (sourceType != null && sourceType.isAssignableTo(targetType)) {
			return value;
		}
		if (conversionService.canConvert(sourceType, targetType)) {
			return conversionService.convert(value, sourceType, targetType);
		}
		if (!String.class.isAssignableFrom(sourceType.getType())) {
			PropertyEditor editor = delegate.findCustomEditor(sourceType.getType(), null);
			if (editor==null) {
				editor = this.getDefaultEditor(sourceType.getType());
			}
			if (editor != null) { // INT-1441
				String text = null;
				synchronized (editor) {
					editor.setValue(value);
					text = editor.getAsText();
				}
				if (String.class.isAssignableFrom(targetType.getClass())) {
					return text;
				}
				return convertValue(text, TypeDescriptor.valueOf(String.class), targetType);
			}
		}
		return delegate.convertIfNecessary(value, targetType.getType());
	}

	private PropertyEditor getDefaultEditor(Class<?> sourceType) {
		PropertyEditor defaultEditor;
		if (this.haveCalledDelegateGetDefaultEditor) {
			defaultEditor= delegate.getDefaultEditor(sourceType);
		}
		else {
			synchronized(this) {
				// not thread-safe - it builds the defaultEditors field in-place (SPR-10191)
				defaultEditor= delegate.getDefaultEditor(sourceType);
			}
			this.haveCalledDelegateGetDefaultEditor = true;
		}
		return defaultEditor;
	}

}