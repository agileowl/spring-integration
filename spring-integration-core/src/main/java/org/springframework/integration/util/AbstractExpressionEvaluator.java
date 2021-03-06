/*
 * Copyright 2002-2011 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.integration.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MapAccessor;
import org.springframework.core.convert.ConversionService;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.Message;
import org.springframework.integration.MessageHandlingException;

/**
 * @author Mark Fisher
 * @author Dave Syer
 * @author Oleg Zhurakousky
 * 
 * @since 2.0
 */
public abstract class AbstractExpressionEvaluator implements BeanFactoryAware {
	
	private final Log logger = LogFactory.getLog(this.getClass());

	private final StandardEvaluationContext evaluationContext = new StandardEvaluationContext();

	private final ExpressionParser expressionParser = new SpelExpressionParser();

	private final BeanFactoryTypeConverter typeConverter = new BeanFactoryTypeConverter();

	private volatile BeanResolver beanResolver;

	public AbstractExpressionEvaluator() {
		this.evaluationContext.setTypeConverter(this.typeConverter);
		this.evaluationContext.addPropertyAccessor(new MapAccessor());
	}

	/**
	 * Specify a BeanFactory in order to enable resolution via <code>@beanName</code> in the expression.
	 */
	public void setBeanFactory(final BeanFactory beanFactory) {
		if (beanFactory != null) {
			this.typeConverter.setBeanFactory(beanFactory);
			if (beanResolver == null) {
				this.evaluationContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
			}
		}
	}
	
	public void setBeanResolver(BeanResolver beanResolver) {
		this.beanResolver = beanResolver;
		this.evaluationContext.setBeanResolver(beanResolver);
	}

	public void setConversionService(ConversionService conversionService) {
		if (conversionService != null) {
			this.typeConverter.setConversionService(conversionService);
		}
	}

	protected StandardEvaluationContext getEvaluationContext() {
		return this.evaluationContext;
	}

	protected <T> T evaluateExpression(Expression expression, Message<?> message, Class<T> expectedType) {
		try {
			return evaluateExpression(expression, (Object) message, expectedType);
		}
		catch (EvaluationException e) {
			Throwable cause = e.getCause();
			if (this.logger.isDebugEnabled()) {
				logger.debug("SpEL Expression evaluation failed with EvaluationException.", e);
			}
			throw new MessageHandlingException(message, "Expression evaluation failed: "
					+ expression.getExpressionString(), cause == null ? e : cause);
		}
		catch (Exception e) {
			if (this.logger.isDebugEnabled()) {
				logger.debug("SpEL Expression evaluation failed with Exception." + e);
			}
			throw new MessageHandlingException(message, "Expression evaluation failed: "
					+ expression.getExpressionString(), e);
		}
	}

	protected Object evaluateExpression(String expression, Object input) {
		return this.evaluateExpression(expression, input, (Class<?>) null);
	}

	protected <T> T evaluateExpression(String expression, Object input, Class<T> expectedType) {
		return this.expressionParser.parseExpression(expression).getValue(this.evaluationContext, input, expectedType);
	}

	protected Object evaluateExpression(Expression expression, Object input) {
		return this.evaluateExpression(expression, input, (Class<?>) null);
	}

	protected <T> T evaluateExpression(Expression expression, Class<T> expectedType) {
		return expression.getValue(this.evaluationContext, expectedType);
	}

	protected Object evaluateExpression(Expression expression) {
		return expression.getValue(this.evaluationContext);
	}

	protected <T> T evaluateExpression(Expression expression, Object input, Class<T> expectedType) {
		return expression.getValue(this.evaluationContext, input, expectedType);
	}

}
