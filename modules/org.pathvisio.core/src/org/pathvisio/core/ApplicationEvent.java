/*******************************************************************************
 * PathVisio, a tool for data visualization and analysis using biological pathways
 * Copyright 2006-2022 BiGCaT Bioinformatics, WikiPathways
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package org.pathvisio.core;

import java.util.EventObject;

/**
 * Events generated by Engine, upon opening, saving and loading pathways
 *
 *  TODO: probably APPLICATION_CLOSE will move to a different class in the future.
 */
public class ApplicationEvent extends EventObject
{
	public enum Type
	{
		/** for events just after a pathway was opened */
		PATHWAY_OPENED,
		/** for events just after a new pathway was created */
		PATHWAY_NEW,
		/** for events when Application is closed */
		APPLICATION_CLOSE, 

		//TODO: what is difference between VPATHWAY_NEW and VPATHWAY_CREATED???
		VPATHWAY_CREATED,
		VPATHWAY_OPENED,
		VPATHWAY_NEW,
		PATHWAY_SAVE,

		/** whenever a VPathway is closed. */
		VPATHWAY_DISPOSED
	}

	private Type type;
	
	/**
	   Event type, e.g. PATHWAY_OPENED when a pathway was opened
	 */
	public Type getType()
	{
		return type;
	}

	public ApplicationEvent(Object source, Type type) 
	{
		super(source);
		this.type = type;
	}
}
