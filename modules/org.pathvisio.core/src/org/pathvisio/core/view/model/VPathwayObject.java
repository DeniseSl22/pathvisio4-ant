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
package org.pathvisio.core.view.model;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.pathvisio.libgpml.biopax.PublicationXref;
import org.pathvisio.libgpml.debug.DebugList;
import org.pathvisio.libgpml.model.PathwayObject;
import org.pathvisio.libgpml.model.PathwayObjectEvent;
import org.pathvisio.libgpml.model.PathwayObjectListener;
import org.pathvisio.libgpml.model.type.LineStyleType;

/**
 * This class is a parent class for all graphics that can be added to a
 * VPathway.
 */
public abstract class VPathwayObject extends VElement implements PathwayObjectListener {
	protected PathwayObject gdata = null;

	// children is everything that moves when this element is dragged.
	// includes Citation and State
	private List<VElement> children = new DebugList<VElement>();

	private VCitation citation;

	public VPathwayObject(VPathwayModel canvas, PathwayObject o) {
		super(canvas);
		o.addListener(this);
		gdata = o;
		checkCitation();
	}

	protected VCitation createCitation() {
		return new VCitation(canvas, this, new Point2D.Double(1, -1));
	}

	public final void checkCitation() {
		List<PublicationXref> xrefs = gdata.getBiopaxReferenceManager().getPublicationXRefs();
		if (xrefs.size() > 0 && citation == null) {
			citation = createCitation();
			children.add(citation);
		} else if (xrefs.size() == 0 && citation != null) {
			citation.destroy();
			children.remove(citation);
			citation = null;
		}

		if (citation != null) {
			// already exists, no need to create / destroy
			// just redraw...
			citation.markDirty();
		}
	}

	public void markDirty() {
		super.markDirty();
		for (VElement child : children)
			child.markDirty();
	}

	protected VCitation getCitation() {
		return citation;
	}

	/**
	 * Gets the model representation (PathwayElement) of this class
	 * 
	 * @return
	 */
	public PathwayObject getPathwayObject() {
		return gdata;
	}

	boolean listen = true;

	public void gmmlObjectModified(PathwayObjectEvent e) {
		if (listen) {
			markDirty(); // mark everything dirty
			checkCitation();
		}
	}

	public Area createVisualizationRegion() {
		return new Area(getVBounds());
	}

	/**
	 * Get the x-coordinate of the center point of this object adjusted to the
	 * current zoom factor
	 * 
	 * @return the center x-coordinate
	 */
	public double getVCenterX() {
		return vFromM(gdata.getCenterX());
	}

	/**
	 * Get the y-coordinate of the center point of this object adjusted to the
	 * current zoom factor
	 *
	 * @return the center y-coordinate
	 */
	public double getVCenterY() {
		return vFromM(gdata.getCenterY());
	}

	/**
	 * Get the x-coordinate of the left side of this object adjusted to the current
	 * zoom factor, but not taking into account rotation
	 * 
	 * @note if you want the left side of the rotated object's boundary, use
	 *       {@link #getVShape(true)}.getX();
	 * @return
	 */
	public double getVLeft() {
		return vFromM(gdata.getLeft());
	}

	/**
	 * Get the width of this object adjusted to the current zoom factor, but not
	 * taking into account rotation
	 * 
	 * @note if you want the width of the rotated object's boundary, use
	 *       {@link #getVShape(true)}.getWidth();
	 * @return
	 */
	public double getVWidth() {
		return vFromM(gdata.getWidth());
	}

	/**
	 * Get the y-coordinate of the top side of this object adjusted to the current
	 * zoom factor, but not taking into account rotation
	 * 
	 * @note if you want the top side of the rotated object's boundary, use
	 *       {@link #getVShape(true)}.getY();
	 * @return
	 */
	public double getVTop() {
		return vFromM(gdata.getTop());
	}

	/**
	 * Get the height of this object adjusted to the current zoom factor, but not
	 * taking into account rotation
	 * 
	 * @note if you want the height of the rotated object's boundary, use
	 *       {@link #getVShape(true)}.getY();
	 * @return
	 */
	public double getVHeight() {
		return vFromM(gdata.getHeight());
	}

	/**
	 * Get the direct view to model translation of this shape
	 * 
	 * @param rotate Whether to take into account rotation or not
	 * @return
	 */
	abstract protected Shape getVShape(boolean rotate);

	/**
	 * Get the rectangle that represents the bounds of the shape's direct
	 * translation from model to view, without taking into account rotation. Default
	 * implementation is equivalent to <code>getVShape(false).getBounds2D();</code>
	 */
	protected Rectangle2D getVScaleRectangle() {
		return getVShape(false).getBounds2D();
	}

	/**
	 * Scales the object to the given rectangle, by taking into account the rotation
	 * (given rectangle will be rotated back before scaling)
	 * 
	 * @param r
	 */
	protected abstract void setVScaleRectangle(Rectangle2D r);

	/**
	 * Default implementation returns the rotated shape. Subclasses may override
	 * (e.g. to include the stroke)
	 * 
	 * @see {@link VElement#calculateVOutline()}
	 */
	protected Shape calculateVOutline() {
		return getVShape(true);
	}

	/**
	 * Returns the fontstyle to create a java.awt.Font
	 * 
	 * @return the fontstyle, or Font.PLAIN if no font is available
	 */
	public int getVFontStyle() {
		int style = Font.PLAIN;
		if (gdata.getFontName() != null) {
			if (gdata.getFontWeight()) {
				style |= Font.BOLD;
			}
			if (gdata.getFontStyle()) {
				style |= Font.ITALIC;
			}
		}
		return style;
	}

	protected void destroy() {
		super.destroy();
		gdata.removeListener(this);
		for (VElement child : children) {
			child.destroy();
		}
		children.clear();
		citation = null;

		// View should not remove its model
//		Pathway parent = gdata.getParent();
//		if(parent != null) parent.remove(gdata);
	}

	/**
	 * Returns the z-order from the model
	 */
	protected int getZOrder() {
		return gdata.getZOrder();
	}

	protected Color getLineColor() {
		Color linecolor = gdata.getColor();
		/*
		 * the selection is not colored red when in edit mode it is possible to see a
		 * color change immediately
		 */
		if (isSelected() && !canvas.isEditMode()) {
			linecolor = selectColor;
		}
		return linecolor;
	}

	protected void setLineStyle(Graphics2D g) {
		int ls = gdata.getLineStyle();
		float lt = (float) vFromM(gdata.getLineWidth());
		if (ls == LineStyleType.SOLID) {
			g.setStroke(new BasicStroke(lt));
		} else if (ls == LineStyleType.DASHED) {
			g.setStroke(
					new BasicStroke(lt, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10, new float[] { 4, 4 }, 0));
		} else if (ls == LineStyleType.DOUBLE) {
			g.setStroke(new CompositeStroke(new BasicStroke(lt * 2), new BasicStroke(lt)));
		}
	}

	public void addChild(VElement elt) {
		children.add(elt);
	}

	public void removeChild(VElement elt) {
		children.remove(elt);
	}

}

/**
 * Generates double line stroke, e.g., for cellular compartment shapes.
 *
 */
final class CompositeStroke implements Stroke {
	private Stroke stroke1, stroke2;

	public CompositeStroke(Stroke stroke1, Stroke stroke2) {
		this.stroke1 = stroke1;
		this.stroke2 = stroke2;
	}

	public Shape createStrokedShape(Shape shape) {
		return stroke2.createStrokedShape(stroke1.createStrokedShape(shape));
	}
}