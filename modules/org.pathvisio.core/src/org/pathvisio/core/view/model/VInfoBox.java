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

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import org.pathvisio.libgpml.model.PathwayObject;

/**
 * //TODO: view.InfoBox corresponds in some ways to
 * model.PathwayElement(ObjectType.MAPPINFO) and in some ways to
 * model.PathwayElement(ObjectType.INFOBOX).
 * This confusion is rooted in inconsistencies in GPML.
 * This should be cleaned up one day.
 */
public class VInfoBox extends VPathwayObject {
	static final int V_SPACING = 5;
	static final int H_SPACING = 10;
	static final int INITIAL_SIZE = 200;

	//Elements not stored in gpml
	String fontName			= "Times New Roman";
	String fontWeight		= "regular";
	static final double M_INITIAL_FONTSIZE	= 12.0;

	int sizeX = 1;
	int sizeY = 1; //Real size is calculated on first call to draw()

	public VInfoBox (VPathwayModel canvas, PathwayObject o) {
		super(canvas, o);
		canvas.setMappInfo(this);
	}

	protected VCitation createCitation()
	{
		return new VCitation(canvas, this, new Point2D.Double(1, 0));
	}


	//public Point getBoardSize() { return new Point((int)gdata.getMBoardWidth(), (int)gdata.getMBoardHeight()); }

	int getVFontSize()
	{
		return (int)(vFromM(M_INITIAL_FONTSIZE));
	}

	protected void vMoveBy(double vdx, double vdy)
	{
//		markDirty();
		gdata.setTop (gdata.getTop()  + mFromV(vdy));
		gdata.setLeft (gdata.getLeft() + mFromV(vdx));
//		markDirty();
	}

	public void doDraw(Graphics2D g)
	{
		Font f = new Font(fontName, Font.PLAIN, getVFontSize());
		Font fb = new Font(f.getFontName(), Font.BOLD, f.getSize());

		if(isSelected()) {
			g.setColor(selectColor);
		}

		//Draw Name, Organism, Data-Source, Version, Author, Maintained-by, Email, Availability and last modified
		String[][] text = new String[][] {
				{"Title: ", gdata.getTitle()},
				{"Maintained by: ", gdata.getMaintainer()},
				{"Email: ", gdata.getEmail()},
				{"Availability: ", gdata.getCopyright()},
				{"Last modified: ", gdata.getLastModified()},
				{"Organism: ", gdata.getOrganism()},
				{"Data Source: ", gdata.getSource()}
			};


		int shift = 0;
		int vLeft = (int)vFromM(gdata.getLeft());
		int vTop = (int)vFromM(gdata.getTop());

		int newSizeX = sizeX;
		int newSizeY = sizeY;

		FontRenderContext frc = g.getFontRenderContext();
		for(String[] s : text)
		{
			if(s[1] == null || s[1].equals("")) continue; //Skip empty labels
			TextLayout tl0 = new TextLayout(s[0], fb, frc);
			TextLayout tl1 = new TextLayout(s[1], f, frc);
			Rectangle2D b0 = tl0.getBounds();
			Rectangle2D b1 = tl1.getBounds();
			shift += (int)Math.max(b0.getHeight(), b1.getHeight()) + V_SPACING;
			g.setFont(fb);
			tl0.draw(g, vLeft, vTop + shift);
			g.setFont(f);

			tl1.draw(g, vLeft + (int)b0.getWidth() + H_SPACING, vTop + shift);

			// add 10 for safety
			newSizeX = Math.max(
				newSizeX,
				(int)b0.getWidth() + (int)b1.getWidth() + H_SPACING + 10);
		}
		newSizeY = shift + 10; // add 10 for safety

		// if the size was incorrect, mark dirty and draw again.
		// note: we can't draw again right away because the clip rect
		// is set to a too small region.
		if (newSizeX != sizeX || newSizeY != sizeY)
		{
			sizeX = newSizeX;
			sizeY = newSizeY;
			markDirty();
		}
	}

	protected Shape getVShape(boolean rotate) {
		double vLeft = vFromM(gdata.getLeft());
		double vTop = vFromM(gdata.getTop());
		double vW = sizeX;
		double vH = sizeY;
		if(vW == 1 && vH == 1) {
			vW = INITIAL_SIZE;
			vH = INITIAL_SIZE;
		}
		return new Rectangle2D.Double(vLeft, vTop, vW, vH);
	}

	protected void setVScaleRectangle(Rectangle2D r) {
		//Do nothing, can't resize infobox
	}
}