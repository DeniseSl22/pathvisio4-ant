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
package org.pathvisio.libgpml.model;

import java.io.File;
import java.io.OutputStream;

import org.jdom2.Document;
import org.jdom2.Element;
import org.pathvisio.libgpml.io.ConverterException;

/**
 * Interface for GPML writing.
 * 
 * @author unknown
 */
public interface GpmlFormatWriter extends GpmlFormatVersion {
	
	Document createJdom(PathwayModel data) throws ConverterException;

	Element createJdomElement(PathwayElement o) throws ConverterException;

	void writeToXml(PathwayModel pwy, File file, boolean validate) throws ConverterException;

	void writeToXml(PathwayModel pwy, OutputStream out, boolean validate) throws ConverterException;
}
