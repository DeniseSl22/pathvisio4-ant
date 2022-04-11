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
package org.pathvisio.gui;

import java.awt.Color;
import java.awt.Shape;
import java.awt.Graphics;
import java.awt.BasicStroke;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.Ellipse2D;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.Graphics2D;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.UIManager;

import java.awt.event.FocusEvent;
import java.awt.event.FocusAdapter;

import org.pathvisio.core.util.ColorPalette;
import org.pathvisio.core.view.model.DefaultTemplates;
import org.pathvisio.gui.CommonActions.NewElementAction;
import org.pathvisio.gui.ImageButtonShapes.ButtonList;
import org.pathvisio.libgpml.model.shape.ShapeCatalog;
import org.pathvisio.libgpml.model.shape.ShapeCatalog.Internal;
import org.pathvisio.libgpml.model.type.DataNodeType;
import org.pathvisio.libgpml.util.ColorUtils;

/**
 * this is the buttons on the drop-down panel
 *
 * @author bing
 */
public class ImageTextButton extends JButton {

	Shape imageShape;
	Color imageColor;

	public ImageTextButton(Action a) {
		super();
		this.setAction(a);
		this.setRolloverEnabled(true);
		initRolloverListener();
		this.imageShape = getImageShape(); // set shape
		this.imageColor = getImageColor(); // set color
		this.setTextString(getText()); // set text
		// UI Design
		Dimension dim = new Dimension(33, 33);
		this.setSize(dim);
		this.setPreferredSize(dim);
		this.setMinimumSize(dim);
		this.setMaximumSize(dim);
		this.setMargin(new Insets(0, 0, 0, 0));
		this.setContentAreaFilled(false);
		this.setFont(new Font("Tahoma", Font.PLAIN, 9)); // UI Design
	}

	/**
	 * Initiates rollover listener.
	 */
	protected void initRolloverListener() {
		MouseListener l = new MouseAdapter() {

			public void mouseEntered(MouseEvent e) {
				setContentAreaFilled(true);
				getModel().setRollover(true);
			}

			public void mouseExited(MouseEvent e) {
				setContentAreaFilled(false);
			}

		};
		addMouseListener(l);
	}

	/**
	 * Paints this button.
	 */
	@Override
	protected void paintComponent(Graphics g) {
		long startTime = System.nanoTime();
//		System.out.println(this.getAction().toString());
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		if (this.getAction().toString() == "Undefined") {
			g2.setStroke(
					new BasicStroke(1, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10, new float[] { 4, 4 }, 0));
		}
		this.setForeground(imageColor);
		g2.setPaint(imageColor);
		if (this.getAction().toString() != "Label") {
			g2.draw(imageShape);
		}
		g2.setColor(new Color(255, 255, 255, 0));
		g2.dispose();
		long estimatedTime = System.nanoTime() - startTime;
//		System.out.println(estimatedTime);
	}

	/**
	 * Sets String text for this button
	 * 
	 * @param txt the text.
	 */
	private void setTextString(String txt) {
		if (txt.equals("Undefined")) {
			txt = "?";
		} else if (txt.equals("Hexagon")) {
			txt = "6";
		} else if (txt.equals("Octagon")) {
			txt = "8";
		} else if (txt.equals("Coronavirus")) {
			txt = null;
		} else if (txt.equals("CellIcon")) {
			txt = null;
		} else if (txt.equals("DNAIcon")) {
			txt = null;
		} else if (txt.length() > 5) {
			txt = txt.substring(0, 4);
		}
		this.setText(txt);

	}

	// TODO
	private Shape getImageShape() {
		Shape sh = null;
		switch (getText()) {
		case "Pathway": // Concepts
		case "Disease":
		case "Phenotype":
		case "Event":
		case "Undefined":
		case "Cell":
		case "Organ":
			sh = new RoundRectangle2D.Double(4, 6, 24, 20, 8, 8);
			break;
		case "Alias":
			sh = new Ellipse2D.Double(4, 6, 24, 20);
			break;
		case "Label": // nothing
			break;
		case "Hexagon":
			sh = formatShape(ShapeCatalog.getRegularPolygon(6, 17, 17), 8, 8, 17, 17);
			break;
		case "Octagon":
			sh = formatShape(ShapeCatalog.getRegularPolygon(8, 17, 17), 8, 8, 17, 17);
			break;
//		case "Coronavirus":
//			sh = formatShape(ShapeCatalog.getPluggableShape(Internal.CORONAVIRUS), 4, 4, 24, 24);
//			break;
//		case "CellIcon":
//			sh = formatShape(ImageButtonShapes.getButtonShape(ButtonList.CELL_ICON_BUTTON), 4, 4, 24, 19);
//			break;
		case "DNAIcon":
			sh = formatShape(ShapeCatalog.getPluggableShape(Internal.DNA), 12, 4, 8, 24);
			break;
		default:
			sh = new Rectangle(4, 6, 24, 20);
			break;
		}
		return sh;
	}

	// TODO
	private Color getImageColor() {
		Color color = null;
		switch (getText()) {
		case "Metabolite":
			color = ColorPalette.WP_BLUE;
			break;
		case "Pathway":
			color = ColorPalette.WP_GREEN;
			break;
		default:
			color = ColorPalette.WP_BLACK;
			break;
		}
		return color;
	}

	/**
	 * Returns given shape at the given position with the given dimensions.
	 * 
	 * @param shape  the shape to reformat.
	 * @param xshift the horizontal shift.
	 * @param yshift the vertical shirt.
	 * @param w      the desired width.
	 * @param h      the desired height.
	 * @return
	 */
	private Shape formatShape(Shape shape, double xshift, double yshift, double w, double h) {
		Rectangle r = shape.getBounds();
		AffineTransform at = new AffineTransform();
		at.translate(-r.x + xshift, -r.y + yshift);
		at.scale(w / r.width, h / r.height);
		return at.createTransformedShape(shape);

	}

}