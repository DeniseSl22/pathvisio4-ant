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

import java.awt.Component;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;

import org.pathvisio.core.view.VCitation;
import org.pathvisio.core.view.VDataNode;
import org.pathvisio.core.view.Graphics;
import org.pathvisio.core.view.VGroup;
import org.pathvisio.core.view.Handle;
import org.pathvisio.core.view.VInfoBox;
import org.pathvisio.core.view.VLabel;
import org.pathvisio.core.view.VLineElement;
import org.pathvisio.core.view.MouseEvent;
import org.pathvisio.core.view.VState;
import org.pathvisio.core.view.VAnchor;
import org.pathvisio.core.view.VPathwayModel;
import org.pathvisio.core.view.VPathwayElement;
import org.pathvisio.core.view.VPathwayModelEvent;
import org.pathvisio.core.view.VPathwayModelListener;
import org.pathvisio.core.view.ViewActions;
import org.pathvisio.core.view.ViewActions.PositionPasteAction;
import org.pathvisio.gui.CommonActions.AddLiteratureAction;
import org.pathvisio.gui.CommonActions.EditLiteratureAction;
import org.pathvisio.gui.CommonActions.PropertiesAction;
import org.pathvisio.gui.dialogs.PathwayElementDialog;
import org.pathvisio.gui.view.VPathwaySwing;
import org.pathvisio.libgpml.model.type.AnchorShapeType;
import org.pathvisio.libgpml.model.type.ConnectorType;
import org.pathvisio.libgpml.model.type.GroupType;

/**
 * Implementation of {@link VPathwayModelListener} that handles righ-click events to
 * show a popup menu when a {@link VPathwayElement} is clicked.
 *
 * This class is responsible for maintaining a list of {@link PathwayElementMenuHook}'s,
 * There should be a single Listener per MainPanel, possibly listening to multiple {@link VPathwayModel}'s.
 */
public class PathwayElementMenuListener implements VPathwayModelListener {

	private List<PathwayElementMenuHook> hooks = new ArrayList<PathwayElementMenuHook>();
	

	public void addPathwayElementMenuHook(PathwayElementMenuHook hook)
	{
		hooks.add (hook);
	}

	public void removePathwayElementMenuHook(PathwayElementMenuHook hook)
	{
		hooks.remove(hook);
	}

	/**
	 * This should be implemented by plug-ins
	 * that wish to hook into the Pathway Element Menu
	 */
	public interface PathwayElementMenuHook
	{
		public void pathwayElementMenuHook (VPathwayElement e, JPopupMenu menu);
	}

	/**
	 * Get an instance of a {@link JPopupMenu} for a given {@link VPathwayElement}
	 * @param e The {@link VPathwayElement} to create the popup menu for. If e is an instance of
	 * {@link Handle}, the menu is based on the parent element.
	 * @return The {@link JPopupMenu} for the given pathway element
	 */
	private JPopupMenu getMenuInstance(SwingEngine swingEngine, VPathwayElement e) {
		if(e instanceof VCitation) return null;

		JMenu pathLitRef = null;
		if(e instanceof Handle) {
			e = ((Handle)e).getParent();
			pathLitRef = new JMenu("Literature for pathway");
		}

		VPathwayModel vp = e.getDrawing();
		VPathwaySwing component = (VPathwaySwing)vp.getWrapper();
		ViewActions vActions = vp.getViewActions();

		JPopupMenu menu = new JPopupMenu();

		//Don't show delete if the element cannot be deleted
		if(!(e instanceof VInfoBox)) {
			menu.add(vActions.delete1);
		}

		JMenu selectMenu = new JMenu("Select");
		selectMenu.add(vActions.selectAll);
		selectMenu.add(vActions.selectDataNodes);
		selectMenu.add(vActions.selectInteractions);
		selectMenu.add(vActions.selectLines);
		selectMenu.add(vActions.selectShapes);
		selectMenu.add(vActions.selectLabels);
		menu.add(selectMenu);
		menu.addSeparator();
		
		// new feature to copy and paste with the right-click menu
		menu.add(vActions.copy);
		
		PositionPasteAction a = vActions.positionPaste;
		Point loc = MouseInfo.getPointerInfo().getLocation();
		SwingUtilities.convertPointFromScreen(loc, component);
		a.setPosition(loc);
		
		menu.add(a);
		menu.addSeparator();
		

		//Only show group/ungroup when multiple objects or a group are selected
		if((e instanceof VGroup)) {
			GroupType s = ((VGroup)e).getPathwayElement().getGroupStyle();
			if(s == GroupType.GROUP) {
				menu.add(vActions.toggleGroup);
			} else {
				menu.add(vActions.toggleComplex);
			}
			menu.addSeparator();
		} else if(vp.getSelectedGraphics().size() > 1) {
			menu.add(vActions.toggleGroup);
			menu.add(vActions.toggleComplex);
		}

		if (e instanceof VDataNode)
		{
			menu.add(vActions.addState);
		}
		if (e instanceof VState)
		{
			menu.add(vActions.removeState);
		}
		
		if((e instanceof VLineElement)) {
			final VLineElement line = (VLineElement)e;

			menu.add(vActions.addAnchor);
			
			if (line.getPathwayElement().getConnectorType() == ConnectorType.SEGMENTED) {
				menu.add(vActions.addWaypoint);
				menu.add(vActions.removeWaypoint);
			}
			
			JMenu typeMenu = new JMenu("Line type");

			ButtonGroup buttons = new ButtonGroup();

			ActionListener listener = new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					line.getPathwayElement().setConnectorType(
							ConnectorType.fromName(e.getActionCommand())
					);
				}
			};
			for(ConnectorType t : ConnectorType.getValues()) {
				JRadioButtonMenuItem mi = new JRadioButtonMenuItem(t.getName());
				mi.setActionCommand(t.getName());
				mi.setSelected(t.equals(line.getPathwayElement().getConnectorType()));
				mi.addActionListener(listener);
				typeMenu.add(mi);
				buttons.add(mi);
			}
			menu.add(typeMenu);
		}

		if((e instanceof VAnchor)) {
			final VAnchor anchor = ((VAnchor)e);

			JMenu anchorMenu = new JMenu("Anchor type");
			ButtonGroup buttons = new ButtonGroup();

			ActionListener listener = new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					anchor.getMAnchor().setShape(
							AnchorShapeType.fromName(e.getActionCommand()));
				}
			};

			for(AnchorShapeType at : AnchorShapeType.getValues()) {
				JRadioButtonMenuItem mi = new JRadioButtonMenuItem(at.getName());
				mi.setActionCommand(at.getName());
				mi.setSelected(at.equals(anchor.getMAnchor().getShape()));
				mi.addActionListener(listener);
				anchorMenu.add(mi);
				buttons.add(mi);
			}

			menu.add(anchorMenu);
		}

		JMenu orderMenu = new JMenu("Order");
		orderMenu.add(vActions.orderBringToFront);
		orderMenu.add(vActions.orderSendToBack);
		orderMenu.add(vActions.orderUp);
		orderMenu.add(vActions.orderDown);
		menu.add(orderMenu);

		if(e instanceof Graphics) {
			JMenu litMenu = new JMenu("Literature");
			litMenu.add(new AddLiteratureAction(swingEngine, component, e));
			litMenu.add(new EditLiteratureAction(swingEngine, component, e));
			menu.add(litMenu);

			menu.addSeparator();
			menu.add(new PropertiesAction(swingEngine, component,e));
		}
		
		if(pathLitRef != null) {
			menu.addSeparator();
			pathLitRef.add(new AddLiteratureAction(swingEngine, component, swingEngine.getEngine().getActiveVPathwayModel().getMappInfo()));
			pathLitRef.add(new EditLiteratureAction(swingEngine, component, swingEngine.getEngine().getActiveVPathwayModel().getMappInfo()));
			menu.add(pathLitRef);
		}
		
		if(e instanceof VLabel) {
			menu.addSeparator();
			menu.add(new CommonActions.AddHrefAction(e, swingEngine));
		}
		
		menu.addSeparator();

		// give plug-ins a chance to add menu items.
		for (PathwayElementMenuHook hook : hooks)
		{
			hook.pathwayElementMenuHook (e, menu);
		}

		return menu;
	}

	private SwingEngine swingEngine;

	PathwayElementMenuListener(SwingEngine swingEngine)
	{
		this.swingEngine = swingEngine;
	}

	public void vPathwayModelEvent(VPathwayModelEvent e) {
		switch(e.getType()) {
		case ELEMENT_CLICKED_DOWN:
			if(e.getAffectedElement() instanceof VCitation) {
				VCitation c = (VCitation)e.getAffectedElement();
				PathwayElementDialog d = swingEngine.getPopupDialogHandler().getInstance(
						c.getParent().getPathwayElement(), false, null, null
				);
				d.selectPathwayElementPanel(PathwayElementDialog.TAB_LITERATURE);
				d.setVisible(true);
				break;
			}
		case ELEMENT_CLICKED_UP:
			assert(e.getVPathwayModel() != null);
			assert(e.getVPathwayModel().getWrapper() instanceof VPathwaySwing);
			
			if(e.getMouseEvent().isPopupTrigger()) {
				Component invoker = (VPathwaySwing)e.getVPathwayModel().getWrapper();
				MouseEvent me = e.getMouseEvent();
				JPopupMenu m = getMenuInstance(swingEngine, e.getAffectedElement());
				if(m != null) {
					m.show(invoker, me.getX(), me.getY());
				}
			}
			break;
		}
	}
}
