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
package org.pathvisio.core.util;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.pathvisio.libgpml.model.DataNode;
import org.pathvisio.libgpml.model.Label;
import org.pathvisio.libgpml.model.PathwayModel;
import org.pathvisio.libgpml.model.Shape;
import org.pathvisio.libgpml.model.shape.IShape;
import org.pathvisio.libgpml.model.shape.ShapeType;
import org.pathvisio.libgpml.model.type.DataNodeType;
import org.pathvisio.libgpml.util.ColorUtils;
import org.pathvisio.libgpml.util.Utils;

/**
 * Color Theme class. For coloring of pathway model objects.
 * 
 * NB: Sets colors to given Theme. Preserves all other graphics attributes.
 * 
 * TODO How to set group color....
 * 
 * @author finterly
 */
public class Theme {

	String name; 
	// ================================================================================
	// Available Themes
	// ================================================================================
	public static final String WIKIPATHWAYS = "Wikipathways";
	public static final String WIKIPATHWAYS_MIN = "Wikipathways Minimal";

	// ================================================================================
	// Color-able Fields
	// ================================================================================
	private Color colorBackground;
	private Color colorDefault;
	private Color colorMetabolite;
	private Color colorPathway;
	private Color colorDataNodeFill;
	private Color colorLabel;
	private Color colorShapeFill;
	private Color colorShapeGrey;
	private Color colorShapeDarkGrey;
	private boolean colorShapes = false;

	// ================================================================================
	// Constructors
	// ================================================================================
	public Theme(String theme) {
		this.name = theme;
		if (theme == WIKIPATHWAYS) {
			colorBackground = ColorPalette.WP_WHITE;
			colorDefault = ColorPalette.WP_BLACK;
			colorMetabolite = ColorPalette.WP_BLUE;
			colorPathway = ColorPalette.WP_GREEN;
			colorDataNodeFill = ColorPalette.WP_WHITE;
			colorLabel = ColorPalette.WP_BLACK;
			colorShapeFill = ColorPalette.TRANSPARENT;
			colorShapeGrey = ColorPalette.WP_CUSTOM_PV_MGREY;
			colorShapeDarkGrey = ColorPalette.WP_DGREY;
		}else if (theme == WIKIPATHWAYS_MIN) {
			colorBackground = ColorPalette.WP_WHITE;
			colorDefault = ColorPalette.WP_BLACK;
			colorMetabolite = ColorPalette.WP_BLUE;
			colorPathway = ColorPalette.WP_DGREEN;
			colorDataNodeFill = ColorPalette.WP_WHITE;
			colorLabel = ColorPalette.WP_BLACK;
			colorShapeFill = ColorPalette.TRANSPARENT;
			colorShapeGrey = ColorPalette.WP_CUSTOM_PV_MGREY;
			colorShapeDarkGrey = ColorPalette.WP_DGREY;
			colorShapes = true;
		}
	}

	// ================================================================================
	// Color PathwayModel
	// ================================================================================
	/**
	 * @param p
	 * @param theme
	 */
	public void colorPathwayModel(PathwayModel p) {
		p.getPathway().setBackgroundColor(colorBackground);
		for (DataNode d : p.getDataNodes()) {
			setInitialColors(d);
		}
		// TODO States
		if (colorShapes) {
			for (Shape s : p.getShapes()) {
				setInitialColors(s);
			}
		}
//		for (Label l : p.getLabels()) { TODO 
////			l.setFillColor(colorTransparent);
//			l.setTextColor(colorLabel);
//		}
	}

	// ================================================================================
	// Color DataNodes
	// ================================================================================
	/**
	 * Sets text, border, and fill color.
	 * 
	 * @param e the data node.
	 */
	public void setInitialColors(DataNode e) {
		DataNodeType type = e.getType();
		e.setFillColor(colorDataNodeFill);
		if (type == DataNodeType.PATHWAY) {
			e.setTextColor(colorPathway);
			e.setBorderColor(colorPathway);
		} else if (type == DataNodeType.METABOLITE) {
			e.setTextColor(colorMetabolite);
			e.setBorderColor(colorMetabolite);
		} else {
			e.setTextColor(colorDefault);
			e.setBorderColor(colorDefault);
		}
	}

	// ================================================================================
	// Color Shapes
	// ================================================================================
	public static final Set<ShapeType> CELL_COMPONENT_SET = new HashSet<>(Arrays.asList(ShapeType.CELL,
			ShapeType.NUCLEUS, ShapeType.ENDOPLASMIC_RETICULUM, ShapeType.GOLGI_APPARATUS, ShapeType.MITOCHONDRIA,
			ShapeType.SARCOPLASMIC_RETICULUM, ShapeType.ORGANELLE, ShapeType.VESICLE, ShapeType.EXTRACELLULAR));

	// miscellaneous shapes
	public static final Set<ShapeType> MISC_SHAPE_SET = new HashSet<>(
			Arrays.asList(ShapeType.CORONAVIRUS, ShapeType.DNA, ShapeType.RNA, ShapeType.CELL_ICON));

	/**
	 * Sets text, border, and fill color.
	 * 
	 * @param shape
	 */
	public void setInitialColors(Shape e) {
		IShape type = e.getShapeType();
		e.setFillColor(colorShapeFill);
		if (CELL_COMPONENT_SET.contains(type)) {
			e.setTextColor(colorShapeGrey);
			e.setBorderColor(colorShapeGrey);
		} else if (MISC_SHAPE_SET.contains(type)) {
			e.setTextColor(colorShapeDarkGrey);
			e.setBorderColor(colorShapeDarkGrey);
		} else {
			e.setTextColor(colorDefault);
			e.setBorderColor(colorDefault);
		}
	}
	
	/**
	 * TODO
	 */
	@Override
	public String toString() {
		return name;
	}

}