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

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.Timer;

import org.pathvisio.core.Engine;
import org.pathvisio.core.preferences.GlobalPreference;
import org.pathvisio.core.preferences.PreferenceManager;
import org.pathvisio.core.view.KeyEvent;
import org.pathvisio.core.view.LayoutType;
import org.pathvisio.core.view.MouseEvent;
import org.pathvisio.core.view.VElementMouseEvent;
import org.pathvisio.core.view.VElementMouseListener;
import org.pathvisio.core.view.model.SelectionBox.SelectionListener;
import org.pathvisio.core.view.model.VPathwayModelEvent.VPathwayModelEventType;
import org.pathvisio.core.view.model.ViewActions.KeyMoveAction;
import org.pathvisio.core.view.model.ViewActions.TextFormattingAction;
import org.pathvisio.libgpml.biopax.BiopaxElement;
import org.pathvisio.libgpml.debug.Logger;
import org.pathvisio.libgpml.model.LineElement;
import org.pathvisio.libgpml.model.PathwayModel;
import org.pathvisio.libgpml.model.PathwayObject;
import org.pathvisio.libgpml.model.PathwayModelEvent;
import org.pathvisio.libgpml.model.PathwayModelListener;
import org.pathvisio.libgpml.model.GraphLink.LinkableTo;
import org.pathvisio.libgpml.model.PathwayModel.StatusFlagEvent;
import org.pathvisio.libgpml.model.PathwayObject.Anchor;
import org.pathvisio.libgpml.model.PathwayObject.LinePoint;
import org.pathvisio.libgpml.model.type.GroupType;
import org.pathvisio.libgpml.model.type.ObjectType;
import org.pathvisio.libgpml.util.Utils;

/**
 * This class implements and handles a drawing. Graphics objects are stored in
 * the drawing and can be visualized. The class also provides methods for mouse
 * and key event handling.
 *
 * It's necessary to call PreferenceManager.init() before you can instantiate
 * this class.
 */
public class VPathwayModel implements PathwayModelListener {
	private static final double FUZZY_SIZE = 8; // fuzz-factor around mouse cursor
	static final int ZORDER_SELECTIONBOX = Integer.MAX_VALUE;
	static final int ZORDER_HANDLE = Integer.MAX_VALUE - 1;

	// flags for cursor change if mouse is over a
	// label with href and ctrl button is pressed
	private boolean stateCtrl = false;
	private boolean stateEntered = false;
	private VElement lastEnteredElement = null;

	private boolean selectionEnabled = true;

	private PathwayModel temporaryCopy = null;

	/**
	 * Retuns true if snap to anchors is enabled
	 */
	public boolean isSnapToAnchors() {
		return PreferenceManager.getCurrent().getBoolean(GlobalPreference.SNAP_TO_ANCHOR);
	}

	/**
	 * Returns true if the selection capability of this VPathway is enabled
	 */
	public boolean getSelectionEnabled() {
		return selectionEnabled;
	}

	/**
	 * You can disable the selection capability of this VPathway by passing false.
	 * This is not used within Pathvisio, but it is meant for embedding VPathway in
	 * other applications, where selections may not be needed.
	 */
	public void setSelectionEnabled(boolean value) {
		selectionEnabled = value;
	}

	private VPathwayModelWrapper parent; // may be null

	/**
	 * All objects that are visible on this mapp, including the handles but
	 * excluding the legend, mappInfo and selectionBox objects
	 */
	private List<VElement> drawingObjects;
	private List<VElement> toAdd = new ArrayList<VElement>();

	/**
	 * Obtain all VPathwayElements on this VPathway
	 */
	public List<VElement> getDrawingObjects() {
		return drawingObjects;
	}

	/**
	 * The {@link VElement} that is pressed last mouseDown event}
	 */
	private VElement pressedObject = null;

	/**
	 * {@link VInfoBox} object that contains information about this pathway,
	 * currently only used for information in PropertyPanel (TODO: has to be
	 * implemented to behave the same as any Graphics object when displayed on the
	 * drawing)
	 */
	private VInfoBox infoBox;

	private PathwayModel data;

	/**
	 * Returns the associated org.pathvisio.model.Pathway object
	 */
	public PathwayModel getPathwayModel() {
		return data;
	}

	SelectionBox selection;

	private boolean editMode = true;

	/**
	 * Checks if this drawing is in edit mode
	 *
	 * @return false if in edit mode, true if not
	 */
	public boolean isEditMode() {
		return editMode;
	}

	/**
	 * Constructor for this class.
	 *
	 * @param parent Optional gui-specific wrapper for this VPathway
	 */
	public VPathwayModel(VPathwayModelWrapper parent) {
		// NOTE: you need to call PreferenceManager.init() at application start,
		// before instantiating a VPathway
		// This used to be called by Engine.init(), but not anymore.
		// TODO: make preferencemanager a non-static object, so this check is obsolete.
		if (PreferenceManager.getCurrent() == null) {
			throw new InstantiationError("Please call PreferenceManager.init() before instantiating a VPathway");
		}
		this.parent = parent;

		drawingObjects = new ArrayList<VElement>();

		selection = new SelectionBox(this);

		// Code that uses VPathway have to initialize
		// the keyboard actions explicitly, if necessary.
		// registerKeyboardActions();
	}

	/**
	 * This will cause a complete redraw of the pathway to be scheduled. The redraw
	 * will happen as soon as all other swing events are processed.
	 * <p>
	 * Use this only after large changes (e.g. loading a new pathway, applying a new
	 * visualization method) as it is quite slow.
	 */
	public void redraw() {
		if (parent != null)
			parent.redraw();
	}

	public VPathwayModelWrapper getWrapper() {
		return parent;
	}

	/**
	 * Map the contents of a single data object to this VPathway.
	 */
	private VPathwayObject fromModelElement(PathwayObject o) {
		VPathwayObject result = null;
		switch (o.getObjectType()) {
		case DATANODE:
			result = new VDataNode(this, o);
			break;
		case SHAPE:
			result = new VShape(this, o);
			break;
		case LINE:
			result = new VLineElement(this, o);
			break;
		case GRAPHLINE:
			result = new VLineElement(this, o);
			break;
		case MAPPINFO:
			VInfoBox mi = new VInfoBox(this, o);
			result = mi;
			mi.markDirty();
			break;
		case LABEL:
			result = new VLabel(this, o);
			break;
		case GROUP:
			result = new VGroup(this, o);
			break;
		case STATE:
			result = new VState(this, o);
			break;
		default:
			break;
		}
		return result;
	}

	/**
	 * used by undo manager.
	 */
	public void replacePathway(PathwayModel originalState) {
		boolean changed = data.hasChanged();

		clearSelection();
		drawingObjects = new ArrayList<VElement>();
		// transfer selectionBox with corresponding listeners
		SelectionBox newSelection = new SelectionBox(this);
		for (Iterator<SelectionListener> i = selection.getListeners().iterator(); i.hasNext();) {
			SelectionListener l = i.next();
			newSelection.addListener(l);
			i.remove();
		}
		selection = newSelection;
		data.removeListener(this);
		pressedObject = null;
		data.transferStatusFlagListeners(originalState);
		data = null;
		pointsMtoV = new HashMap<LinePoint, VPoint>();
		fromModel(originalState);

		if (changed != originalState.hasChanged()) {
			data.fireStatusFlagEvent(new StatusFlagEvent(originalState.hasChanged()));
		}
	}

	/**
	 * Maps the contents of a pathway to this VPathway
	 */
	public void fromModel(PathwayModel aData) {
		Logger.log.trace("Create view structure");

		data = aData;
		for (PathwayObject o : data.getDataObjects()) {
			fromModelElement(o);
		}

		// data.fireObjectModifiedEvent(new PathwayEvent(null,
		// PathwayEvent.MODIFIED_GENERAL));
		fireVPathwayEvent(new VPathwayModelEvent(this, VPathwayModelEventType.MODEL_LOADED));
		data.addListener(this);
		undoManager.setPathwayModel(data);
		addScheduled();
		Logger.log.trace("Done creating view structure");
	}

	Template newTemplate = null;

	/**
	 * Method to set the template that provides the new graphics type that has to be
	 * added next time the user clicks on the drawing.
	 *
	 * @param t A template that provides the elements to be added
	 */
	public void setNewTemplate(Template t) {
		newTemplate = t;
	}

	/**
	 * Adds object boundaries to the "dirty" area, the area which needs to be
	 * redrawn. The redraw will not happen immediately, but will be scheduled on the
	 * event dispatch thread.
	 */
	void addDirtyRect(Rectangle2D ar) {
		if (parent != null)
			parent.redraw(ar.getBounds());
	}

	/**
	 * Deprecated: Does nothing, redraws are now scheduled by Swing. call
	 * addDirtyRect() or redraw() instead, or remove calls to this altogether
	 */
	@Deprecated
	public void redrawDirtyRect() {
	}

	/**
	 * Sets the MappInfo containing information on the pathway
	 *
	 * @param mappInfo
	 */
	public void setMappInfo(VInfoBox mappInfo) {
		this.infoBox = mappInfo;
	}

	/**
	 * Gets the MappInfo containing information on the pathway
	 */
	public VInfoBox getMappInfo() {
		return infoBox;
	}

	/**
	 * Adds an element to the drawing
	 *
	 * @param o the element to add
	 */
	public void addObject(VElement o) {
		toAdd.add(o);
	}

	/**
	 * Gets the view representation {@link VPathwayObject} of the given model element
	 * {@link PathwayObject}
	 *
	 * @param e
	 * @return the {@link VPathwayObject} representing the given {@link PathwayObject} or
	 *         <code>null</code> if no view is available
	 */
	public VPathwayObject getPathwayElementView(PathwayObject e) {
		// TODO: store Graphics in a hashmap to improve speed
		for (VElement ve : drawingObjects) {
			if (ve instanceof VPathwayObject) {
				VPathwayObject ge = (VPathwayObject) ve;
				if (ge.getPathwayObject() == e)
					return ge;
			}
		}
		return null;
	}

	Map<LinePoint, VPoint> pointsMtoV = new HashMap<LinePoint, VPoint>();

	protected VPoint getPoint(LinePoint mPoint) {
		return pointsMtoV.get(mPoint);
	}

	public VPoint newPoint(LinePoint mPoint, VLineElement line) {
		VPoint p = pointsMtoV.get(mPoint);
		if (p == null) {
			p = new VPoint(this, mPoint, line);
			pointsMtoV.put(mPoint, p);
		}
		return p;
	}

	/**
	 * Set this drawing to editmode
	 *
	 * @param editMode true if editmode has to be enabled, false if disabled (view
	 *                 mode)
	 */
	public void setEditMode(boolean editMode) {
		this.editMode = editMode;
		if (!editMode) {
			clearSelection();
		}

		redraw();
		VPathwayModelEventType type = editMode ? VPathwayModelEventType.EDIT_MODE_ON : VPathwayModelEventType.EDIT_MODE_OFF;
		fireVPathwayEvent(new VPathwayModelEvent(this, type));
	}

	private double zoomFactor = 1.0;

	/**
	 * Get the current zoomfactor used. 1 means 100%, 1 gpml unit = 1 pixel 2 means
	 * 200%, 0.5 gpml unit = 1 pixel
	 *
	 * To distinguish between model coordinates and view coordinates, we prefix all
	 * coordinates with either v or m (or V or M). For example:
	 *
	 * mTop = gdata.getMTop(); vTop = GeneProduct.getVTop();
	 *
	 * Calculations done on M's and V's should always match. The only way to convert
	 * is to use the functions mFromV and vFromM.
	 *
	 * Correct: mRight = mLeft + mWidth; Wrong: mLeft += vDx; Fixed: mLeft +=
	 * mFromV(vDx);
	 *
	 * @return the current zoomfactor
	 */
	public double getZoomFactor() {
		return zoomFactor;
	}

	/**
	 * same as getZoomFactor, but in %
	 *
	 * @return
	 */
	public double getPctZoom() {
		return zoomFactor * 100;
	}

	/**
	 * Sets the drawings zoom in percent
	 *
	 * @param pctZoomFactor zoomfactor in percent
	 * @see #getFitZoomFactor() for fitting the pathway inside the viewport.
	 */
	public void setPctZoom(double pctZoomFactor) {
		zoomFactor = pctZoomFactor / 100.0;
		for (VElement vpe : drawingObjects) {
			vpe.zoomChanged();
		}
		if (parent != null)
			parent.resized();
	}

	public void centeredZoom(double pctZoomFactor) {
		if (parent != null) {
			Rectangle vr = parent.getViewRect();

			double mx = mFromV(vr.getCenterX());
			double my = mFromV(vr.getCenterY());
			Logger.log.info("center: " + mx + ", " + my);
			setPctZoom(pctZoomFactor);
			parent.scrollCenterTo((int) vFromM(mx), (int) vFromM(my));
		} else
			setPctZoom(pctZoomFactor);
	}

	public void zoomToCursor(double pctZoomFactor, Point cursor) {
		if (parent == null)
			return;

		// offset between mouse and center of the viewport
		double vDeltax = cursor.getX() - parent.getViewRect().getCenterX();
		double vDeltay = cursor.getY() - parent.getViewRect().getCenterY();
		;

		// model coordinates where the mouse is pointing at
		double mx = mFromV(cursor.getX());
		double my = mFromV(cursor.getY());

		// adjust zoom
		setPctZoom(pctZoomFactor);

		// put mx, my back under the mouse
		parent.scrollCenterTo((int) (vFromM(mx) - vDeltax), (int) (vFromM(my) - vDeltay));
	}

	/**
	 * Calculate the zoom factor that would make the pathway fit in the viewport.
	 */
	public double getFitZoomFactor() {
		double result;
		Dimension drawingSize = new Dimension(getVWidth(), getVHeight());
		Dimension viewportSize = getWrapper().getViewRect().getSize();
		result = (int) Math.min(getPctZoom() * (double) viewportSize.width / drawingSize.width,
				getPctZoom() * (double) viewportSize.height / drawingSize.height);
		return result;
	}

	public void setPressedObject(VElement o) {
		pressedObject = o;
	}

	private LinkAnchor currentLinkAnchor;

	/**
	 * @arg p2d point where mouse is at
	 */
	private void linkPointToObject(Point2D p2d, Handle g) {
		if (dragUndoState == DRAG_UNDO_CHANGE_START) {
			dragUndoState = DRAG_UNDO_CHANGED;
		}
		hideLinkAnchors();
		VPoint p = (VPoint) g.getAdjustable();
		VLineElement l = p.getLine();
		PathwayObject pe = l.getPathwayObject();
		List<LinkProvider> objects = getLinkProvidersAt(p2d);
		// Fix for preventing grouped line to link to its own group
		// Remove the group from the list of linkproviders
		// Also remove the line anchors to prevent linking a line
		// to it's own anchors
		if (g.getAdjustable() instanceof VPoint) {
			if (pe.getGroupRef() != null) {
				PathwayObject group = getPathwayModel().getGroupById(pe.getGroupRef());
				objects.remove(getPathwayElementView(group));
			}
			for (VAnchor va : l.getVAnchors()) {
				objects.remove(va);
			}
		}

		LinkableTo idc = null;
		for (LinkProvider lp : objects) {
			if (lp instanceof VAnchor && ((VAnchor) lp).getMAnchor().getShape().isDisallowLinks()) {
				break;
			}
			lp.showLinkAnchors();
			LinkAnchor la = lp.getLinkAnchorAt(p2d);
			if (la != null) {
				// Set graphRef
				la.link(p.getMPoint());
				idc = la.getGraphIdContainer();
				if (currentLinkAnchor != null) {
					currentLinkAnchor.unhighlight();
				}
				la.highlight();
				currentLinkAnchor = la;
				break;
			}
		}

		if (idc == null && p.getMPoint().isLinked()) {
			String graphRef = p.getMPoint().getElementRef();
			p.getMPoint().unlink();
			if (currentLinkAnchor != null) {
				if (pe instanceof LineElement && isAnotherLineLinked(graphRef, (LineElement) pe)) {

				} else if (currentLinkAnchor.getGraphIdContainer() instanceof Anchor
						&& currentLinkAnchor.getGraphIdContainer().getElementId().equals(graphRef)) {
					currentLinkAnchor.getGraphIdContainer().setElementId(null);
				}
				currentLinkAnchor.unhighlight();
			}
		}
	}

	private boolean isAnotherLineLinked(String graphRef, LineElement currLine) {
		for (PathwayObject element : getPathwayModel().getDataObjects()) {
			if (element instanceof LineElement) {
				if (element.equals(currLine)) {
					continue;
				}
				for (LinePoint point : element.getLinePoints()) {
					if (point.getElementRef() == null) {
						// skip point
					} else if (graphRef != null && point.getElementRef().equals(graphRef)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void hideLinkAnchors() {
		for (VElement pe : getDrawingObjects()) {
			if (pe instanceof LinkProvider) {
				((LinkProvider) pe).hideLinkAnchors();
			}
		}
	}

	private boolean snapModifierPressed;

	/**
	 * Check whether the key is pressed to restrict handle movement. When the key is
	 * down:
	 * <li>lines snap to certain angles (but only when preference is on).
	 * <li>rotation handles on shapes snap to certain angles
	 * <li>shape snaps to a fixed aspect ratio
	 * <p>
	 * 
	 * @see GlobalPreference#SNAP_TO_ANGLE for the global setting
	 * @see GlobalPreference#SNAP_TO_ANGLE_STEP for the angle step to be used
	 * @return
	 */
	public boolean isSnapModifierPressed() {
		return snapModifierPressed;
	}

	private int vPreviousX;
	private int vPreviousY;
	private boolean isDragging;

	/**
	 * handles mouse movement
	 */
	public void mouseMove(MouseEvent ve) {
		snapModifierPressed = ve.isKeyDown(MouseEvent.M_SHIFT);
		// If draggin, drag the pressed object
		// And only when the right button isn't clicked
		if (pressedObject != null && isDragging && !ve.isKeyDown(java.awt.event.MouseEvent.BUTTON3_DOWN_MASK)) {
			if (dragUndoState == DRAG_UNDO_CHANGE_START) {
				dragUndoState = DRAG_UNDO_CHANGED;
			}
			double vdx = ve.getX() - vPreviousX;
			double vdy = ve.getY() - vPreviousY;
			if (pressedObject instanceof Handle) {
				((Handle) (pressedObject)).vMoveTo(ve.getX(), ve.getY());
			} else {
				pressedObject.vMoveBy(vdx, vdy);
			}

			vPreviousX = ve.getX();
			vPreviousY = ve.getY();

			if (pressedObject instanceof Handle && newTemplate == null
					&& ((Handle) pressedObject).getAdjustable() instanceof VPoint) {
				linkPointToObject(new Point2D.Double(ve.getX(), ve.getY()), (Handle) pressedObject);
			}
		} else {
			List<VElement> objects = getObjectsAt(new Point2D.Double(ve.getX(), ve.getY()));

			// Process mouseexit events
			processMouseExitEvents(ve, objects);

			// Process mouseenter events
			processMouseEnterEvents(ve, objects);
		}

		hoverManager.reset(ve);
	}

	private void processMouseEnterEvents(MouseEvent ve, List<VElement> objects) {
		for (VElement vpe : objects) {
			if (!lastMouseOver.contains(vpe)) {
				lastMouseOver.add(vpe);
				stateEntered = true;
				if (vpe instanceof VLabel && !((VLabel) vpe).gdata.getHref().equals("")) {
					lastEnteredElement = vpe;
				} else {
					fireVElementMouseEvent(new VElementMouseEvent(this, VElementMouseEvent.TYPE_MOUSEENTER, vpe, ve));
				}
			}
		}
		if (lastEnteredElement != null) {
			fireHyperlinkUpdate(lastEnteredElement);
		}
	}

	private void processMouseExitEvents(MouseEvent ve, List<VElement> objects) {
		Set<VElement> toRemove = new HashSet<VElement>();

		for (VElement vpe : lastMouseOver) {
			if (!objects.contains(vpe)) {
				toRemove.add(vpe);
				stateEntered = false;
				if (lastEnteredElement == vpe) {
					fireHyperlinkUpdate(lastEnteredElement);
					lastEnteredElement = null;
				} else {
					fireVElementMouseEvent(new VElementMouseEvent(this, VElementMouseEvent.TYPE_MOUSEEXIT, vpe, ve));
				}

			}
		}

		lastMouseOver.removeAll(toRemove);
	}

	private Set<VElement> lastMouseOver = new HashSet<VElement>();
	private HoverManager hoverManager = new HoverManager();

	private class HoverManager implements ActionListener {
		static final int DELAY = 1000; // tooltip delay in ms
		boolean tooltipDisplayed = false;

		MouseEvent lastEvent = null;

		Timer timer;

		public HoverManager() {
			timer = new Timer(DELAY, this);
		}

		public void actionPerformed(ActionEvent e) {
			if (!tooltipDisplayed) {
				fireVPathwayEvent(new VPathwayModelEvent(VPathwayModel.this, getObjectsAt(lastEvent.getLocation()),
						lastEvent, VPathwayModelEventType.ELEMENT_HOVER));
				tooltipDisplayed = true;
			}
		}

		void reset(MouseEvent e) {
			lastEvent = e;
			tooltipDisplayed = false;
			timer.restart();
		}

		void stop() {
			timer.stop();
		}
	}

	/**
	 * Handles movement of objects with the arrow keys
	 *
	 * @param ks
	 */
	public void moveByKey(KeyStroke ks, int increment) {
		List<VPathwayObject> selectedGraphics = getSelectedNonGroupGraphics();

		if (selectedGraphics.size() > 0) {

			switch (ks.getKeyCode()) {
			case 37:
				undoManager.newAction("Move object");
				selection.vMoveBy(-increment, 0);
				break;
			case 39:
				undoManager.newAction("Move object");
				selection.vMoveBy(increment, 0);
				break;
			case 38:
				undoManager.newAction("Move object");
				selection.vMoveBy(0, -increment);
				break;
			case 40:
				undoManager.newAction("Move object");
				selection.vMoveBy(0, increment);
			}
		}
	}

	public void selectObject(VElement o) {
		clearSelection();
		selection.addToSelection(o);
	}

	// opens href of a Label with ctrl + click
	private boolean openHref(MouseEvent e, VElement o) {
		if (e.isKeyDown(128) && o != null && o instanceof VLabel) {
			String href = ((VLabel) o).gdata.getHref();
			if (selection.getSelection().size() < 1 && !href.equals("")) {
				fireVPathwayEvent(new VPathwayModelEvent(this, o, VPathwayModelEventType.HREF_ACTIVATED));
				return true;
			}
		}
		return false;
	}

	/**
	 * Handles mouse Pressed input
	 */
	public void mouseDown(MouseEvent e) {
		VElement vpe = getObjectAt(e.getLocation());
		if (!openHref(e, vpe)) {
			// setFocus();
			vDragStart = new Point(e.getX(), e.getY());
			temporaryCopy = (PathwayModel) data.clone();

			if (editMode) {
				if (newTemplate != null) {
					newObject(e.getLocation());
					// SwtGui.getCurrent().getWindow().deselectNewItemActions();
				} else {
					pressedObject = vpe;
					editObject(e);
				}
			} else {
				mouseDownViewMode(e);
			}
			if (pressedObject != null) {
				fireVPathwayEvent(
						new VPathwayModelEvent(this, pressedObject, e, VPathwayModelEventType.ELEMENT_CLICKED_DOWN));
			}
		}
	}

	/**
	 * Handles mouse Released input
	 */
	public void mouseUp(MouseEvent e) {
		if (isDragging) {
			if (dragUndoState == DRAG_UNDO_CHANGED) {
				assert (temporaryCopy != null);
				// further specify the type of undo event,
				// depending on the type of object being dragged
				String message = "Drag Object";
				if (pressedObject instanceof Handle) {
					if (((Handle) pressedObject).getFreedom() == Handle.Freedom.ROTATION) {
						message = "Rotate Object";
					} else {
						message = "Resize Object";
					}
				}
				undoManager.newAction(new UndoAction(message, temporaryCopy));
				temporaryCopy = null;
			}
			resetHighlight();
			hideLinkAnchors();
			if (selection.isSelecting()) { // If we were selecting, stop it
				selection.stopSelecting();
			}
			// check if we placed a new object by clicking or dragging
			// if it was a click, give object the initial size.
			else if (newObject != null && Math.abs(vDragStart.x - e.getX()) <= MIN_DRAG_LENGTH
					&& Math.abs(vDragStart.y - e.getY()) <= MIN_DRAG_LENGTH) {
				newObject.setInitialSize();
			}
			newObject = null;
			setNewTemplate(null);
		}
		isDragging = false;
		dragUndoState = DRAG_UNDO_NOT_RECORDING;
		if (pressedObject != null) {
			fireVPathwayEvent(new VPathwayModelEvent(this, pressedObject, e, VPathwayModelEventType.ELEMENT_CLICKED_UP));
		}
	}

	/**
	 * Handles mouse entered input
	 */
	public void mouseDoubleClick(MouseEvent e) {
		VElement o = getObjectAt(e.getLocation());
		if (o != null) {
			Logger.log.trace("Fire double click event to " + listeners.size());
			for (VPathwayModelListener l : listeners) {
				Logger.log.trace("\t " + l.hashCode() + ", " + l);
			}
			fireVPathwayEvent(new VPathwayModelEvent(this, o, VPathwayModelEventType.ELEMENT_DOUBLE_CLICKED));
		}
	}

	/**
	 * Paints all components in the drawing. This method is called automatically in
	 * the painting process. This method will draw opaquely, meaning it will erase
	 * the background.
	 * 
	 * @param g2d the graphics device to draw on. The method will not draw outside
	 *            the clipping area.
	 */
	public void draw(Graphics2D g2d) {
		addScheduled();
		cleanUp();

		try {
			// save original, non-clipped, to pass on to VPathwayEvent
			Graphics2D g2dFull = (Graphics2D) g2d.create();

			// we only redraw the part within the clipping area.
			Rectangle area = g2d.getClipBounds();
			if (area == null) {
				Dimension size = parent == null ? new Dimension(getVWidth(), getVHeight())
						: parent.getViewRect().getSize(); // Draw the visible area
				area = new Rectangle(0, 0, size.width, size.height);
			}

			// erase the background
			g2d.setColor(java.awt.Color.WHITE);
			g2d.fillRect(area.x, area.y, area.width, area.height);

			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

			g2d.clip(area);
			g2d.setColor(java.awt.Color.BLACK);
			Collections.sort(drawingObjects);
			cleanUp();
			for (VElement o : drawingObjects) {
				if (o.vIntersects(area)) {
					if (checkDrawAllowed(o)) {
						o.draw((Graphics2D) g2d.create());
						fireVPathwayEvent(new VPathwayModelEvent(this, o, (Graphics2D) g2dFull.create(),
								VPathwayModelEventType.ELEMENT_DRAWN));
					}
				}
			}
		} catch (ConcurrentModificationException ex) {
			// guard against messing up repaint event completely
			Logger.log.error("Concurrent modification", ex);
		}
	}

	boolean checkDrawAllowed(VElement o) {
		if (isEditMode())
			return true;
		else
			return !(o instanceof Handle || (o == selection && !isDragging));
	}

	/**
	 * deselect all elements on the drawing and resets the selectionbox.
	 */
	public void clearSelection() {
		clearSelection(0, 0);
	}

	/**
	 * deselect all elements on the drawing and resets the selectionbox to the given
	 * coordinates Equivalent to {@link SelectionBox#reset(double, double))}
	 */
	private void clearSelection(double x, double y) {
		for (VElement e : drawingObjects)
			e.deselect();
		selection.reset(x, y);
	}

	static final int MULTI_SELECT_MASK = MouseEvent.M_SHIFT
			| (Utils.getOS() == Utils.OS_MAC ? MouseEvent.M_META : MouseEvent.M_CTRL);

	/**
	 * Handles event when on mouseDown in case the drawing is in view mode (does
	 * nothing yet)
	 *
	 * @param e the mouse event to handle
	 */
	private void mouseDownViewMode(MouseEvent e) {
		Point2D p2d = new Point2D.Double(e.getX(), e.getY());

		pressedObject = getObjectAt(p2d);

		if (pressedObject != null) {
			// Shift or Ctrl or Meta pressed, add/remove from selection
			boolean modifierPressed = e.isKeyDown(MULTI_SELECT_MASK);
			doClickSelect(p2d, modifierPressed);
		} else
			startSelecting(p2d);
	}

	/**
	 * Initializes selection, resetting the selectionbox and then setting it to the
	 * position specified
	 *
	 * @param vp - the point to start with the selection
	 */
	void startSelecting(Point2D vp) {
		if (!selectionEnabled)
			return;

		vPreviousX = (int) vp.getX();
		vPreviousY = (int) vp.getY();
		isDragging = true;
		dragUndoState = DRAG_UNDO_NOT_RECORDING;

		clearSelection(vp.getX(), vp.getY());
		selection.startSelecting();
		pressedObject = selection.getCornerHandle();
	}

	/**
	 * Resets highlighting, unhighlights all GmmlDrawingObjects
	 */
	public void resetHighlight() {
		for (VElement o : drawingObjects)
			o.unhighlight();
		redrawDirtyRect();
	}

	/**
	 * Called by MouseDown, when we're in editting mode and we're not adding new
	 * objects prepares for dragging the object
	 * 
	 * @param pressedObject
	 */
	private void editObject(MouseEvent e) {
		// if we clicked on an object
		if (pressedObject != null) {
			// Shift pressed, add/remove from selection
			boolean modifierPressed = e.isKeyDown(MULTI_SELECT_MASK);
			// if our object is an handle, select also it's parent.
			if (pressedObject instanceof Handle) {
				VElement parent = ((Handle) pressedObject).getParent();
				parent.select();
				// Special treatment for anchor
				if (parent instanceof VAnchor) {
					doClickSelect(e.getLocation(), modifierPressed);
				}
			} else {
				doClickSelect(e.getLocation(), modifierPressed);
			}

			// start dragging
			vPreviousX = e.getX();
			vPreviousY = e.getY();

			isDragging = true;
			dragUndoState = DRAG_UNDO_CHANGE_START;
		} else {
			// start dragging selectionbox
			startSelecting(e.getLocation());
		}
	}

	/**
	 * Find the object at a particular location on the drawing
	 *
	 * if you want to get more than one,
	 *
	 * @see #getObjectsAt(Point2D)
	 */
	public VElement getObjectAt(Point2D p2d) {
		int zmax = Integer.MIN_VALUE;
		VElement probj = null;
		for (VElement o : drawingObjects) {
			// first we use vContains, which is good for detecting (non-transparent) shapes
			if (o.vContains(p2d) && o.getZOrder() > zmax) {
				probj = o;
				zmax = o.getZOrder();
			}
		}
		if (probj == null) {
			// if there is nothing at that point, we use vIntersects with a fuzz area,
			// which is good for detecting lines and transparent shapes.
			Rectangle2D fuzz = new Rectangle2D.Double(p2d.getX() - FUZZY_SIZE, p2d.getY() - FUZZY_SIZE, FUZZY_SIZE * 2,
					FUZZY_SIZE * 2);
			for (VElement o : drawingObjects) {
				if (o.vIntersects(fuzz) && o.getZOrder() > zmax) {
					probj = o;
					zmax = o.getZOrder();
				}
			}
		}
		return probj;
	}

	/**
	 * Find all objects at a particular location on the drawing
	 *
	 * if you only need the top object,
	 *
	 * @see #getObjectAt(Point2D)
	 */
	public List<VElement> getObjectsAt(Point2D p2d) {
		List<VElement> result = new ArrayList<VElement>();
		for (VElement o : drawingObjects) {
			if (o.vContains(p2d)) {
				result.add(o);
			}
		}
		return result;
	}

	private List<LinkProvider> getLinkProvidersAt(Point2D p) {
		List<LinkProvider> result = new ArrayList<LinkProvider>();
		for (VElement o : drawingObjects) {
			if (o instanceof LinkProvider && o.getVBounds().contains(p)) {
				result.add((LinkProvider) o);
			}
		}
		return result;
	}

	/**
	 * if modifierPressed is true, the selected object will be added to the
	 * selection, rather than creating a new selection with just one object. if
	 * modifierPressed is true when selecting a Group object, then a new selection
	 * is made of the children, allowing selection into groups.
	 *
	 * modifierPressed should be true when either SHIFT or CTRL/COMMAND is pressed.
	 */
	void doClickSelect(Point2D p2d, boolean modifierPressed) {
		if (!selectionEnabled)
			return;

		if (modifierPressed) {
			if (pressedObject instanceof SelectionBox) { // Object inside selectionbox clicked:
															// pass to selectionbox
				selection.objectClicked(p2d);
			} else if (pressedObject.isSelected()) { // Already in selection:
														// remove
				selection.removeFromSelection(pressedObject);
			} else { // Not in selection:
						// add
				selection.addToSelection(pressedObject);
			}
			pressedObject = selection; // Set dragging to selectionbox
		} else
		// Shift or Ctrl not pressed
		{
			// If pressedobject is not selectionbox:
			// Clear current selection and select pressed object
			if (!(pressedObject instanceof SelectionBox)) {
				clearSelection();
				// If the object is a handle, select the parent instead
				if (pressedObject instanceof Handle) {
					VElement parent = ((Handle) pressedObject).getParent();
					selection.addToSelection((VElement) parent);
				} else {
					selection.addToSelection(pressedObject);
				}
			} else { // Check if clicked object inside selectionbox
				if (selection.getChild(p2d) == null)
					clearSelection();
			}
		}
		redrawDirtyRect();
	}

	/**
	 * pathvisio distinguishes between placing objects with a click or with a drag.
	 * If you don't move the cursor in between the mousedown and mouseup event, the
	 * object is placed with a default initial size.
	 *
	 * vDragStart is used to determine the mousemovement during the click.
	 */
	private Point vDragStart;

	/**
	 * dragUndoState determines what should be done when you release the mouse
	 * button after dragging an object.
	 *
	 * if it is DRAG_UNDO_NOT_RECORDING, it's not necessary to record an event. This
	 * is the case when we were dragging a selection rectangle, or a new object (in
	 * which case the change event was already recorded)
	 *
	 * in other cases, it is set to DRAG_UNDO_CHANGE_START at the start of the drag.
	 * If additional move events occur, the state is changed to DRAG_UNDO_CHANGED.
	 * The latter will lead to recording of the undo event.
	 */
	private static final int DRAG_UNDO_NOT_RECORDING = 0;
	private static final int DRAG_UNDO_CHANGE_START = 1;
	private static final int DRAG_UNDO_CHANGED = 2;

	private int dragUndoState = DRAG_UNDO_NOT_RECORDING;

	/** newly placed object, is set to null again when mouse button is released */
	private PathwayObject newObject = null;

	/** minimum drag length for it to be considered a drag and not a click */
	private static final int MIN_DRAG_LENGTH = 3;

	/**
	 * Add a new object to the drawing {@see VPathway#setNewGraphics(int)}
	 *
	 * @param ve The point where the user clicked on the drawing to add a new
	 *           graphics
	 */
	private void newObject(Point ve) {
		undoManager.newAction("New Object");
		double mx = mFromV((double) ve.x);
		double my = mFromV((double) ve.y);

		PathwayObject[] newObjects = newTemplate.addElements(data, mx, my);

		addScheduled();
		if (newObjects != null && newObjects.length > 0) {
			isDragging = true;
			dragUndoState = DRAG_UNDO_NOT_RECORDING;

			if (newObjects.length > 1) {
				clearSelection();
				// Multiple objects: select all and use selectionbox as dragging object
				for (PathwayObject pwe : newObjects) {
					VPathwayObject g = getPathwayElementView(pwe);
					selection.addToSelection(g);
				}
				pressedObject = selection;
			} else {
				// Single object: select object and use dragelement specified by template
				selectObject(lastAdded);
				pressedObject = newTemplate.getDragElement(this);
			}

			newObject = newTemplate.getDragElement(this) == null ? null : newObjects[0];
			vPreviousX = ve.x;
			vPreviousY = ve.y;

			fireVPathwayEvent(new VPathwayModelEvent(this, lastAdded, VPathwayModelEventType.ELEMENT_ADDED));
			newTemplate.postInsert(newObjects);
		}
		setNewTemplate(null);
	}

	public void mouseEnter(MouseEvent e) {
		hoverManager.reset(e);
	}

	public void mouseExit(MouseEvent e) {
		hoverManager.stop();
	}

	/**
	 * Select all objects of the given class
	 *
	 * @param c The class of the objects to be selected. For example: DataNode,
	 *          Line, etc. May be null, in which case everything is selected.
	 */
	void selectObjects(Class<?> c) {
		clearSelection();
		selection.startSelecting();
		for (VElement vpe : getDrawingObjects()) {
			if (c == null || c.isInstance(vpe)) {
				selection.addToSelection(vpe);
			}

		}
		selection.stopSelecting();
	}

	public void selectObjectsByObjectType(ObjectType ot) {
		clearSelection();
		selection.startSelecting();
		for (PathwayObject pe : getPathwayModel().getDataObjects()) {
			if (pe.getObjectType() == ot) {
				selection.addToSelection(getPathwayElementView(pe));
			}
		}
		selection.stopSelecting();
	}

	/**
	 * select all objects of the pathway.
	 */
	void selectAll() {
		selectObjects(null);
	}

	/**
	 * Responds to ctrl/command-G. First checks for current status of selection with
	 * respect to grouping. If selection is already grouped (members of the same
	 * parent group), then the highest-level (parent) group is removed along with
	 * all references to the group. If the selection is not a uniform group, then a
	 * new group is created and each member or groups of members is set to reference
	 * the new group.
	 *
	 * @param selection
	 * @return If a group is created, or if the items were added to a new group,
	 *         this group is returned. If a group is removed, this method will
	 *         return <code>null</code>.
	 */
	public VGroup toggleGroup(List<VPathwayObject> selection) {
		// groupSelection will be set to true if we are going to add / expand a group,
		// false if we're going to remove a group.
		boolean groupSelection = false;
		Set<String> groupRefList = new HashSet<String>();

		/**
		 * Check group status of current selection
		 */
		for (VPathwayObject g : selection) {
			PathwayObject pe = g.getPathwayObject();
			String ref = pe.getGroupRef();
			// If not a group
			if (pe.getObjectType() != ObjectType.GROUP) {
				// and not a member of a group
				if (ref == null) {
					// then selection needs to be grouped
					groupSelection = true;
				}
				// and is a member of a group
				else {
					// recursively get all parent group references.
					while (ref != null) {
						groupRefList.add(ref);
						PathwayObject refGroup = data.getGroupById(ref);
						ref = refGroup.getGroupRef();
					}
				}
			}
		}
		// If more than one group is present in selection, then selection needs to be
		// grouped
		if (groupRefList.size() > 1) {
			groupSelection = true;
		}

		// In all cases, any old groups in selection should be disolved.
		for (String id : groupRefList) {
			PathwayObject e = data.getGroupById(id);
			if (e != null)
				data.remove(e);
		}

		// If selection was defined as a single group, then we're done.
		// clear the selection from view
		if (!groupSelection) {
			clearSelection();
		}
		// Otherwise, a new group will be formed, replacing any former groups.
		// No more nested or overlapping groups!
		else {
			// Form new group with all selected elementsselectPathwayObjects
			PathwayObject group = PathwayObject.createPathwayElement(ObjectType.GROUP);
			data.add(group);
			group.setGroupType(GroupType.NONE);
			String id = group.createGroupId();

			for (VPathwayObject g : selection) {
				PathwayObject pe = g.getPathwayObject();
				pe.setGroupRef(id);
			}

			// Select new group in view
			VPathwayObject vg = getPathwayElementView(group);
			if (vg != null) {
				clearSelection();
				selectObject(vg);
			}
			return (VGroup) vg;
		}
		return null;
	}

	private void fireHyperlinkUpdate(VElement vpe) {
		int type;
		if (stateEntered && stateCtrl) {
			type = VElementMouseEvent.TYPE_MOUSE_SHOWHAND;
		} else {
			type = VElementMouseEvent.TYPE_MOUSE_NOTSHOWHAND;
		}
		fireVElementMouseEvent(new VElementMouseEvent(this, type, vpe));
	}

	public void keyPressed(KeyEvent e) {
		// Use registerKeyboardActions
		if (KeyEvent.CTRL == e.getKeyCode()) {
			stateCtrl = true;
			if (lastEnteredElement != null) {
				fireHyperlinkUpdate(lastEnteredElement);
			}
		}
	}

	// TODO: should use Toolkit.getMenuShortcutKeyMask(), but
	// that doesn't work in headless mode so screws up automated testing.
	// solution: define keyboard shortcuts elsewhere
	public static final KeyStroke KEY_SELECT_DATA_NODES = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D,
			java.awt.Event.CTRL_MASK);

	public static final KeyStroke KEY_SELECT_INTERACTIONS = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E,
			java.awt.Event.CTRL_MASK);

	public static final KeyStroke KEY_BOLD = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_B,
			java.awt.Event.CTRL_MASK);

	public static final KeyStroke KEY_ITALIC = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_I,
			java.awt.Event.CTRL_MASK);

	public static final KeyStroke KEY_MOVERIGHT = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0);

	public static final KeyStroke KEY_MOVELEFT = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0);

	public static final KeyStroke KEY_MOVEUP = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0);

	public static final KeyStroke KEY_MOVEDOWN = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0);

	public static final KeyStroke KEY_MOVERIGHT_SHIFT = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT,
			java.awt.Event.SHIFT_MASK);

	public static final KeyStroke KEY_MOVELEFT_SHIFT = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT,
			java.awt.Event.SHIFT_MASK);

	public static final KeyStroke KEY_MOVEUP_SHIFT = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP,
			java.awt.Event.SHIFT_MASK);

	public static final KeyStroke KEY_MOVEDOWN_SHIFT = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN,
			java.awt.Event.SHIFT_MASK);

	/**
	 * Get the view actions, a class where several actions related to the view are
	 * stored (delete, select) and where other actions can be registered to a group
	 * (e.g. a group that will be disabled when edit-mode is turned off)
	 *
	 * @return an instance of the {@link ViewActions} class
	 */
	public ViewActions getViewActions() {
		return viewActions;
	}

	/**
	 * Several {@link Action}s related to the view
	 */
	private ViewActions viewActions;

	// Convenience method to register an action that has an accelerator key
	private void registerKeyboardAction(Action a) {
		if (parent == null)
			return;
		KeyStroke key = (KeyStroke) a.getValue(Action.ACCELERATOR_KEY);
		if (key == null)
			throw new RuntimeException("Action " + a + " must have value ACCELERATOR_KEY set");
		parent.registerKeyboardAction(key, a);
	}

	public void registerKeyboardActions(Engine engine) {
		viewActions = new ViewActions(engine, this);

		if (parent != null) {
			registerKeyboardAction(viewActions.copy);
			registerKeyboardAction(viewActions.paste);
			parent.registerKeyboardAction(KEY_SELECT_DATA_NODES, viewActions.selectDataNodes);
			parent.registerKeyboardAction(KEY_SELECT_INTERACTIONS, viewActions.selectInteractions);
			registerKeyboardAction(viewActions.toggleGroup);
			registerKeyboardAction(viewActions.toggleComplex);
			registerKeyboardAction(viewActions.selectAll);
			registerKeyboardAction(viewActions.delete1);
			registerKeyboardAction(viewActions.delete2);
			registerKeyboardAction(viewActions.undo);
			registerKeyboardAction(viewActions.addAnchor);
			registerKeyboardAction(viewActions.orderBringToFront);
			registerKeyboardAction(viewActions.orderSendToBack);
			registerKeyboardAction(viewActions.orderUp);
			registerKeyboardAction(viewActions.orderDown);
			registerKeyboardAction(viewActions.showUnlinked);
			parent.registerKeyboardAction(KEY_MOVERIGHT, new KeyMoveAction(engine, KEY_MOVERIGHT));
			parent.registerKeyboardAction(KEY_MOVERIGHT_SHIFT, new KeyMoveAction(engine, KEY_MOVERIGHT_SHIFT));
			parent.registerKeyboardAction(KEY_MOVELEFT, new KeyMoveAction(engine, KEY_MOVELEFT));
			parent.registerKeyboardAction(KEY_MOVELEFT_SHIFT, new KeyMoveAction(engine, KEY_MOVELEFT_SHIFT));
			parent.registerKeyboardAction(KEY_MOVEUP, new KeyMoveAction(engine, KEY_MOVEUP));
			parent.registerKeyboardAction(KEY_MOVEUP_SHIFT, new KeyMoveAction(engine, KEY_MOVEUP_SHIFT));
			parent.registerKeyboardAction(KEY_MOVEDOWN, new KeyMoveAction(engine, KEY_MOVEDOWN));
			parent.registerKeyboardAction(KEY_MOVEDOWN_SHIFT, new KeyMoveAction(engine, KEY_MOVEDOWN_SHIFT));
			parent.registerKeyboardAction(KEY_BOLD, new TextFormattingAction(engine, KEY_BOLD));
			parent.registerKeyboardAction(KEY_ITALIC, new TextFormattingAction(engine, KEY_ITALIC));
		}
	}

	public void keyReleased(KeyEvent e) {
		// use registerKeyboardActions
		if (KeyEvent.CTRL == e.getKeyCode()) {
			stateCtrl = false;
			if (lastEnteredElement != null) {
				fireHyperlinkUpdate(lastEnteredElement);
			}
		}
	}

	/**
	 * Removes the GmmlDrawingObjects in the ArrayList from the drawing<BR>
	 * Does not remove the model representation!
	 *
	 * @param toRemove The List containing the objects to be removed
	 */
	public void removeDrawingObjects(List<VElement> toRemove) {
		removeDrawingObjects(toRemove, false);
	}

	/**
	 * Removes the GmmlDrawingObjects in the ArrayList from the drawing
	 *
	 * @param toRemove        The List containing the objects to be removed
	 * @param removeFromModel Whether to remove the model representation or not
	 */
	public void removeDrawingObjects(List<VElement> toRemove, boolean removeFromModel) {
		for (VElement o : toRemove) {
			removeDrawingObject(o, removeFromModel);
		}
		selection.fitToSelection();
		cleanUp();
	}

	public void removeDrawingObject(VElement toRemove, boolean removeFromModel) {
		if (toRemove != null) {
			selection.removeFromSelection(toRemove); // Remove from selection
			toRemove.destroy(); // Object will remove itself from the drawing
			if (removeFromModel) {
				if (toRemove instanceof VPathwayObject) {
					// Remove the model object
					data.remove(((VPathwayObject) toRemove).getPathwayObject());
				}
			}
			cleanUp();
		}
	}

	private VPathwayObject lastAdded = null;

	public void pathwayModified(PathwayModelEvent e) {
		switch (e.getType()) {
		case PathwayModelEvent.DELETED:
			VPathwayObject deleted = getPathwayElementView(e.getAffectedData());
			if (deleted != null) {
				if (deleted.getPathwayObject() instanceof LineElement) {
					removeRefFromConnectingAnchors(deleted.getPathwayObject().getStartLinePoint().getElementRef(),
							deleted.getPathwayObject().getEndLinePoint().getElementRef());
				}
				deleted.markDirty();
				removeDrawingObject(deleted, false);
			}
			break;
		case PathwayModelEvent.ADDED:
			lastAdded = fromModelElement(e.getAffectedData());
			if (lastAdded != null) {
				lastAdded.markDirty();
			}
			break;
		case PathwayModelEvent.RESIZED:
			if (parent != null) {
				parent.resized();
			}
			break;
		}
		addScheduled();
		cleanUp();
	}

	/*
	 * when line deleted need to: see if other lines contain same graphIds (this
	 * means that 2 different lines are attached to the same anchor) if so - ignore
	 * graphId otherwise - loop through pathway and find any lines with anchors that
	 * contain GraphIds for the deleted line and remove.
	 */
	private void removeRefFromConnectingAnchors(String graphId1, String graphId2) {
		if (graphId1 == null && (graphId2 == null)) {
			return;
		}
		for (PathwayObject element : getPathwayModel().getDataObjects()) {
			if (element instanceof LineElement) {
				for (LinePoint point : element.getLinePoints()) {
					if (point.getElementRef() == null) {
						// skip point
					} else if (graphId1 != null && point.getElementRef().equals(graphId1)) {
						graphId1 = null;
					} else if (graphId2 != null && point.getElementRef().equals(graphId2)) {
						graphId2 = null;
					}
				}
			}
		}
		if (graphId1 == null && (graphId2 == null)) {
			return;
		}
		for (PathwayObject element : getPathwayModel().getDataObjects()) {
			if (element instanceof LineElement) {
				for (Anchor anchor : element.getAnchors()) {
					if (anchor.getElementId() != null
							&& (anchor.getElementId().equals(graphId1) || anchor.getElementId().equals(graphId2))) {
						anchor.setElementId(null);
					}
				}
			}
		}
	}

	/**
	 * Calculate the board size. Calls {@link VElement#getVBounds()} for
	 * every element and adds all results together to obtain the board size
	 */
	public Dimension calculateVSize() {
		Rectangle2D bounds = new Rectangle2D.Double();
		for (VElement e : drawingObjects) {
			bounds.add(e.getVBounds());
		}
		return new Dimension((int) bounds.getWidth() + 10, (int) bounds.getHeight() + 10);
	}

	/**
	 * Makes a copy of all PathwayElements in current selection, and puts them in
	 * the global clipboard.
	 */
	public void copyToClipboard() {
		List<PathwayObject> result = new ArrayList<PathwayObject>();
		for (VElement g : drawingObjects) {
			if (g.isSelected() && g instanceof VPathwayObject && !(g instanceof SelectionBox)) {
				result.add(((VPathwayObject) g).gdata.copy());
			}
		}
		if (result.size() > 0) {
			if (parent != null)
				parent.copyToClipboard(getPathwayModel(), result);
		}
	}

	/**
	 * Handles aligning layoutTypes ALIGN_*
	 */
	private void alignGraphics(LayoutType alignType, List<VPathwayObject> gs) {
		// first sort either horizontally or vertically
		switch (alignType) {
		case ALIGN_CENTERY:
		case ALIGN_TOP:
			Collections.sort(gs, new YComparator());
			break;
		case ALIGN_LEFT:
		case ALIGN_CENTERX:
			Collections.sort(gs, new XComparator());
			break;
		case ALIGN_BOTTOM:
			Collections.sort(gs, new YComparator());
			Collections.reverse(gs);
			break;
		case ALIGN_RIGHT:
			Collections.sort(gs, new XComparator());
			Collections.reverse(gs);
			break;
		default:
			throw new IllegalArgumentException("This method only handles ALIGN_* layoutTypes");
		}

		// The bounds of the model to view
		// translated shape
		Rectangle2D vBoundsFirst = gs.get(0).getVShape(true).getBounds2D();

		for (int i = 1; i < gs.size(); i++) {
			VPathwayObject g = gs.get(i);
			Rectangle2D vBounds = g.getVShape(true).getBounds2D();

			switch (alignType) {
			case ALIGN_CENTERX:
				g.vMoveBy(vBoundsFirst.getCenterX() - vBounds.getCenterX(), 0);
				break;
			case ALIGN_CENTERY:
				g.vMoveBy(0, vBoundsFirst.getCenterY() - vBounds.getCenterY());
				break;
			case ALIGN_LEFT:
				g.vMoveBy(vBoundsFirst.getX() - vBounds.getX(), 0);
				break;
			case ALIGN_RIGHT:
				g.vMoveBy(vBoundsFirst.getMaxX() - vBounds.getMaxX(), 0);
				break;
			case ALIGN_TOP:
				g.vMoveBy(0, vBoundsFirst.getY() - vBounds.getY());
				break;
			case ALIGN_BOTTOM:
				g.vMoveBy(0, vBoundsFirst.getMaxY() - vBounds.getMaxY());
				break;
			default:
				break;
			}
		}
	}

	/**
	 * Align, stack or scale selected objects based on user-selected layout type
	 */
	public void layoutSelected(LayoutType layoutType) {
		List<VPathwayObject> selectedGraphics = getSelectedNonGroupGraphics();

		if (selectedGraphics.size() > 0) {
			undoManager.newAction(layoutType.getDescription());
			switch (layoutType) {
			case COMMON_WIDTH:
				scaleWidth(selectedGraphics);
				break;
			case COMMON_HEIGHT:
				scaleHeight(selectedGraphics);
				break;
			case ALIGN_CENTERX:
			case ALIGN_CENTERY:
			case ALIGN_TOP:
			case ALIGN_LEFT:
			case ALIGN_RIGHT:
			case ALIGN_BOTTOM:
				alignGraphics(layoutType, selectedGraphics);
				break;
			case STACK_BOTTOM:
			case STACK_TOP:
			case STACK_LEFT:
			case STACK_RIGHT:
			case STACK_CENTERX:
			case STACK_CENTERY:
				stackGraphics(layoutType, selectedGraphics);
				break;
			}

			selection.fitToSelection();
			redrawDirtyRect();
		}
	}

	/**
	 * Stacks a set of objects based on user-selected stack type
	 */
	private void stackGraphics(LayoutType stackType, List<VPathwayObject> gs) {
		// first we sort the selected graphics, either horizontally or vertically
		switch (stackType) {
		case STACK_CENTERX:
		case STACK_LEFT:
		case STACK_RIGHT:
			Collections.sort(gs, new YComparator());
			break;
		case STACK_CENTERY:
		case STACK_TOP:
		case STACK_BOTTOM:
			Collections.sort(gs, new XComparator());
			break;
		default:
			throw new IllegalArgumentException("This method only handles STACK_* layoutTypes");
		}

		for (int i = 1; i < gs.size(); i++) {
			// Get the current and previous graphics objects
			VPathwayObject eCurr = gs.get(i);
			VPathwayObject ePrev = gs.get(i - 1);

			// Get the bounds of the model to view translated shapes
			Rectangle2D vBoundsPrev = ePrev.getVShape(true).getBounds2D();
			Rectangle2D vBoundsCurr = eCurr.getVShape(true).getBounds2D();
			switch (stackType) {
			case STACK_CENTERX:
				eCurr.vMoveBy(vBoundsPrev.getCenterX() - vBoundsCurr.getCenterX(),
						vBoundsPrev.getMaxY() - vBoundsCurr.getY());
				break;
			case STACK_CENTERY:
				eCurr.vMoveBy(vBoundsPrev.getMaxX() - vBoundsCurr.getX(),
						vBoundsPrev.getCenterY() - vBoundsCurr.getCenterY());
				break;
			case STACK_LEFT:
				eCurr.vMoveBy(vBoundsPrev.getX() - vBoundsCurr.getX(), vBoundsPrev.getMaxY() - vBoundsCurr.getY());
				break;
			case STACK_RIGHT:
				eCurr.vMoveBy(vBoundsPrev.getMaxX() - vBoundsCurr.getMaxX(),
						vBoundsPrev.getMaxY() - vBoundsCurr.getY());
				break;
			case STACK_TOP:
				eCurr.vMoveBy(vBoundsPrev.getMaxX() - vBoundsCurr.getX(), vBoundsPrev.getY() - vBoundsCurr.getY());
				break;
			case STACK_BOTTOM:
				eCurr.vMoveBy(vBoundsPrev.getMaxX() - vBoundsCurr.getX(),
						vBoundsPrev.getMaxY() - vBoundsCurr.getMaxY());
				break;
			default:
				break;
			}
		}
	}

	/**
	 * Scales a set of objects by max width
	 */
	private void scaleWidth(List<VPathwayObject> gs) {
		double maxW = 0;
		VPathwayObject gMax = null;
		for (VPathwayObject g : gs) {
			Rectangle2D r = g.getVShape(true).getBounds2D();
			double w = Math.abs(r.getWidth());
			if (w > maxW) {
				gMax = g;
				maxW = w;
			}
		}
		for (VPathwayObject g : gs) {
			if (g == gMax)
				continue;

			Rectangle2D r = g.getVShape(true).getBounds2D();
			double oldWidth = r.getWidth();
			if (oldWidth < 0) {
				r.setRect(r.getX(), r.getY(), -(maxW), r.getHeight());
				g.setVScaleRectangle(r);
				g.vMoveBy((oldWidth + maxW) / 2, 0);
			} else {
				r.setRect(r.getX(), r.getY(), maxW, r.getHeight());
				g.setVScaleRectangle(r);
				g.vMoveBy((oldWidth - maxW) / 2, 0);
			}
		}
	}

	/**
	 * Scales selected objects by max height
	 */
	private void scaleHeight(List<VPathwayObject> gs) {
		double maxH = 0;
		VPathwayObject gMax = null;
		for (VPathwayObject g : gs) {
			Rectangle2D r = g.getVShape(true).getBounds2D();
			double h = Math.abs(r.getHeight());
			if (h > maxH) {
				gMax = g;
				maxH = h;
			}
		}
		for (VPathwayObject g : gs) {
			if (g == gMax)
				continue;

			Rectangle2D r = g.getVShape(true).getBounds2D();
			double oldHeight = r.getHeight();
			if (oldHeight < 0) {
				r.setRect(r.getX(), r.getY(), r.getWidth(), -(maxH));
				g.setVScaleRectangle(r);
				g.vMoveBy(0, (maxH + oldHeight) / 2);
			} else {
				r.setRect(r.getX(), r.getY(), r.getWidth(), maxH);
				g.setVScaleRectangle(r);
				g.vMoveBy(0, (oldHeight - maxH) / 2);
			}
		}
	}

	/**
	 * Move a set of graphics to the top in the z-order stack
	 */
	public void moveGraphicsTop(List<VPathwayObject> gs) {
		Collections.sort(gs, new ZComparator());
		int base = getPathwayModel().getMaxZOrder() + 1;
		for (VPathwayObject g : gs) {
			g.gdata.setZOrder(base++);
		}
	}

	/**
	 * Move a set of graphics to the bottom in the z-order stack
	 */
	public void moveGraphicsBottom(List<VPathwayObject> gs) {
		Collections.sort(gs, new ZComparator());
		int base = getPathwayModel().getMinZOrder() - gs.size() - 1;
		for (VPathwayObject g : gs) {
			g.gdata.setZOrder(base++);
		}
	}

	/**
	 * Looks for overlapping graphics with a higher z-order and moves g on top of
	 * that.
	 */
	public void moveGraphicsUp(List<VPathwayObject> gs) {
		// TODO: Doesn't really work very well with multiple selections
		for (VPathwayObject g : gs) {
			// make sure there is enough space between g and the next
			autoRenumberZOrder();

			int order = g.gdata.getZOrder();
			VPathwayObject nextGraphics = null;
			int nextZ = order;
			for (VPathwayObject i : getOverlappingGraphics(g)) {
				int iorder = i.gdata.getZOrder();
				if (nextGraphics == null && iorder > nextZ) {
					nextZ = iorder;
					nextGraphics = i;
				} else if (nextGraphics != null && iorder < nextZ && iorder > order) {
					nextZ = iorder;
					nextGraphics = i;
				}
			}
			g.gdata.setZOrder(nextZ + 1);
		}
	}

	/**
	 * makes sure there is always a minimum spacing of two between two consecutive
	 * elements, so that we can freely move items in between
	 */
	private void autoRenumberZOrder() {
		List<VPathwayObject> elts = new ArrayList<VPathwayObject>();
		for (VElement vp : drawingObjects) {
			if (vp instanceof VPathwayObject) {
				elts.add((VPathwayObject) vp);
			}
		}
		if (elts.size() < 2)
			return; // nothing to renumber
		Collections.sort(elts, new ZComparator());

		final int spacing = 2;

		int waterLevel = elts.get(0).gdata.getZOrder();
		for (int i = 1; i < elts.size(); ++i) {
			VPathwayObject curr = elts.get(i);
			if (curr.gdata.getZOrder() - waterLevel < spacing) {
				curr.gdata.setZOrder(waterLevel + spacing);
			}
			waterLevel = curr.gdata.getZOrder();
		}
	}

	/**
	 * Looks for overlapping graphics with a lower z-order and moves g on under
	 * that.
	 */
	public void moveGraphicsDown(List<VPathwayObject> gs) {
		// TODO: Doesn't really work very well with multiple selections
		for (VPathwayObject g : gs) {
			// make sure there is enough space between g and the previous
			autoRenumberZOrder();

			int order = g.gdata.getZOrder();
			VPathwayObject nextGraphics = null;
			int nextZ = order;
			for (VPathwayObject i : getOverlappingGraphics(g)) {
				int iorder = i.gdata.getZOrder();
				if (nextGraphics == null && iorder < nextZ) {
					nextZ = iorder;
					nextGraphics = i;
				} else if (nextGraphics != null && iorder > nextZ && iorder < order) {
					nextZ = iorder;
					nextGraphics = i;
				}
			}
			g.gdata.setZOrder(nextZ - 1);
		}
	}

	/**
	 * return a list of Graphics that overlap g. Note that the intersection of
	 * bounding rectangles is used, so the returned list is only an approximation
	 * for rounded shapes.
	 */
	public List<VPathwayObject> getOverlappingGraphics(VPathwayObject g) {
		List<VPathwayObject> result = new ArrayList<VPathwayObject>();
		Rectangle2D r1 = g.getVBounds();

		for (VElement ve : drawingObjects) {
			if (ve instanceof VPathwayObject && ve != g) {
				VPathwayObject i = (VPathwayObject) ve;
				if (r1.intersects(ve.getVBounds())) {
					result.add(i);
				}
			}
		}
		return result;
	}

	/**
	 * Get all elements of the class Graphics that are currently selected
	 *
	 * @return
	 */
	public List<VPathwayObject> getSelectedGraphics() {
		List<VPathwayObject> result = new ArrayList<VPathwayObject>();
		for (VElement g : drawingObjects) {
			if (g.isSelected() && g instanceof VPathwayObject && !(g instanceof SelectionBox)) {
				result.add((VPathwayObject) g);
			}
		}
		return result;
	}

	/**
	 * Get all elements of the class Graphics that are currently selected, excluding
	 * Groups
	 *
	 * @return
	 */
	public List<VPathwayObject> getSelectedNonGroupGraphics() {
		List<VPathwayObject> result = new ArrayList<VPathwayObject>();
		for (VElement g : drawingObjects) {
			if (g.isSelected() && g instanceof VPathwayObject && !(g instanceof SelectionBox) && !((g instanceof VGroup))) {
				result.add((VPathwayObject) g);
			}
		}
		return result;
	}

	/**
	 * Get all selected elements (includes non-Graphics, e.g. Handles)
	 * 
	 * @return
	 */
	public Set<VElement> getSelectedPathwayElements() {
		return selection.getSelection();
	}

	private void generatePasteId(String oldId, Set<String> idset, Map<String, String> idmap, Set<String> newids) {
		if (oldId != null) {
			String x;
			do {
				/*
				 * generate a unique id. at the same time, check that it is not equal to one of
				 * the unique ids that we generated since the start of this method
				 */
				x = data.getUniqueId(idset);
			} while (newids.contains(x));
			newids.add(x); // make sure we don't generate this one
			// again

			idmap.put(oldId, x);
		}
	}

	/**
	 * Generate new id's for a bunch of elements to be pasted, but do not actually
	 * set them. Instead, store these new ids in a map, so that we can later update
	 * both the graphIds and graphReferences, as well as groupIds and
	 * groupReferences.
	 *
	 * idMap and newIds should be an empty map / set. It will be filled by this
	 * method.
	 */
	private void generateNewIds(List<PathwayObject> elements, Map<String, String> idmap, Set<String> newids) {
		for (PathwayObject o : elements) {
			String id = o.getElementId();
			String groupId = o.getGroupId();
			generatePasteId(id, data.getGraphIds(), idmap, newids);
			generatePasteId(groupId, data.getGroupIds(), idmap, newids);

			// For a line, also process the point ids
			if (o.getObjectType() == ObjectType.LINE || o.getObjectType() == ObjectType.GRAPHLINE) {
				for (LinePoint mp : o.getLinePoints())
					generatePasteId(mp.getElementId(), data.getGraphIds(), idmap, newids);
				for (Anchor ma : o.getAnchors())
					generatePasteId(ma.getElementId(), data.getGraphIds(), idmap, newids);
			}
		}
	}

	public void paste(List<PathwayObject> elements) {
		paste(elements, 0, 0);
	}

	/**
	 * Updates all id's and references of a single PathwayElement, using the
	 * provided idMap.
	 *
	 * This will - replace groupId of elements - replace graphId of elements,
	 * anchors and points - replace graphRefs if it's in the map, otherwise set to
	 * null - replace groupRegs if it's in the map, otherwise set to null
	 */
	private void replaceIdsAndRefs(PathwayObject p, Map<String, String> idmap) {
		// set new unique id
		if (p.getElementId() != null) {
			p.setElementId(idmap.get(p.getElementId()));
		}
		for (LinePoint mp : p.getLinePoints()) {
			mp.setElementId(idmap.get(mp.getElementId()));
		}
		for (Anchor ma : p.getAnchors()) {
			ma.setElementId(idmap.get(ma.getElementId()));
		}
		// set new group id
		String gid = p.getGroupId();
		if (gid != null) {
			p.setGroupId(idmap.get(gid));
		}
		// update graphref
		String y = p.getStartElementRef();
		if (y != null) {
			if (idmap.containsKey(y)) {
				p.setStartElementRef(idmap.get(y));
			} else {
				p.setStartElementRef(null);
			}
		}
		y = p.getEndElementRef();
		if (y != null) {
			if (idmap.containsKey(y)) {
				p.setEndElementRef(idmap.get(y));
			} else {
				p.setEndElementRef(null);
			}
		}
		y = p.getElementRef();
		if (y != null) {
			if (idmap.containsKey(y)) {
				p.setElementRef(idmap.get(y));
			}
			// If the ref points to an item outside the selection, keep using original!
		}
		// update groupref
		String groupRef = p.getGroupRef();
		if (groupRef != null) {
			if (idmap.containsKey(groupRef)) {
				p.setGroupRef(idmap.get(groupRef));
			} else {
				p.setGroupRef(null);
			}
		}
	}

	public void paste(List<PathwayObject> elements, double xShift, double yShift) {
		undoManager.newAction("Paste");
		clearSelection();

		Map<String, String> idmap = new HashMap<String, String>();
		Set<String> newids = new HashSet<String>();

		// Step 1: generate new unique ids for copied items
		generateNewIds(elements, idmap, newids);

		// Step 2: do the actual copying
		for (PathwayObject o : elements) {
			if (o.getObjectType() == ObjectType.INFOBOX) {
				// we skip infobox because it should be unique in a pathway
				continue;
			}

			if (o.getObjectType() == ObjectType.BIOPAX) {
				// Merge the copied biopax elements with existing
				data.getBiopax().mergeBiopax((BiopaxElement) o);
				continue;
			}

			lastAdded = null;

			if (o.getObjectType() == ObjectType.LINE || o.getObjectType() == ObjectType.GRAPHLINE) {
				for (LinePoint mp : o.getLinePoints()) {
					mp.setX(mp.getX() + xShift);
					mp.setY(mp.getY() + yShift);
				}
			} else {
				o.setLeft(o.getLeft() + xShift);
				o.setTop(o.getTop() + yShift);
			}

			// make another copy to preserve clipboard contents for next paste
			PathwayObject p = o.copy();

			// use the idMap to set consistent new id's
			replaceIdsAndRefs(p, idmap);

			data.add(p); // causes lastAdded to be set
			lastAdded.select();
			if (!(lastAdded instanceof VGroup)) { // avoids "double selecting" grouped objects
				selection.addToSelection(lastAdded);
			}
		}

		// Step 3: refresh connector shapes
		for (PathwayObject o : elements) {
			if (o.getObjectType() == ObjectType.LINE) {
				((LineElement) o).getConnectorShape().recalculateShape(((LineElement) o));
			}
		}
		moveGraphicsTop(getSelectedGraphics());
		redrawDirtyRect();
	}

	public void pasteFromClipboard() {
		if (isEditMode()) { // Only paste in edit mode
			if (parent != null)
				parent.pasteFromClipboard();
		}
	}

	/**
	 * paste from clip board at the current cursor position
	 */
	public void positionPasteFromClipboard(Point cursorPosition) {
		if (isEditMode()) {
			if (parent != null)
				parent.positionPasteFromClipboard(cursorPosition);
		}
	}

	private List<VPathwayModelListener> listeners = new ArrayList<VPathwayModelListener>();

	public void addVPathwayListener(VPathwayModelListener l) {
		if (!listeners.contains(l)) {
			listeners.add(l);
		}
	}

	public void removeVPathwayListener(VPathwayModelListener l) {
		Logger.log.trace(listeners.remove(l) + ": " + l);
	}

	private List<VElementMouseListener> elementListeners = new ArrayList<VElementMouseListener>();

	public void addVElementMouseListener(VElementMouseListener l) {
		if (!elementListeners.contains(l)) {
			elementListeners.add(l);
		}
	}

	public void removeVElementMouseListener(VElementMouseListener l) {
		elementListeners.remove(l);
	}

	protected void fireVElementMouseEvent(VElementMouseEvent e) {
		for (VElementMouseListener l : elementListeners) {
			l.vElementMouseEvent(e);
		}
	}

	/**
	 * Adds a {@link SelectionListener} to the SelectionBox of this VPathway
	 *
	 * @param l The SelectionListener to add
	 */
	public void addSelectionListener(SelectionListener l) {
		selection.addListener(l);
	}

	/**
	 * Removes a {@link SelectionListener} from the SelectionBox of this VPathway
	 *
	 * @param l The SelectionListener to remove
	 */
	public void removeSelectionListener(SelectionListener l) {
		selection.removeListener(l);
	}

	protected void fireVPathwayEvent(VPathwayModelEvent e) {
		for (VPathwayModelListener l : listeners) {
			l.vPathwayModelEvent(e);
		}
	}

	/**
	 * helper method to convert view coordinates to model coordinates
	 */
	public double mFromV(double v) {
		return v / zoomFactor;
	}

	/**
	 * helper method to convert model coordinates to view coordinates
	 */
	public double vFromM(double m) {
		return m * zoomFactor;
	}

	private AffineTransform vFromM = new AffineTransform();

	public java.awt.Shape vFromM(java.awt.Shape s) {
		vFromM.setToScale(zoomFactor, zoomFactor);
		return vFromM.createTransformedShape(s);
	}

	/**
	 * Get width of entire Pathway view (taking into account zoom)
	 */
	public int getVWidth() {
		return data == null ? 0 : (int) vFromM(data.getMappInfo().getBoardWidth());
	}

	/**
	 * Get height of entire Pathway view (taking into account zoom)
	 */
	public int getVHeight() {
		return data == null ? 0 : (int) vFromM(data.getMappInfo().getBoardHeight());
	}

	/** sorts graphics by VCenterY */
	public static class YComparator implements Comparator<VPathwayObject> {
		public int compare(VPathwayObject g1, VPathwayObject g2) {
			if (g1.getVCenterY() == g2.getVCenterY())
				return 0;
			else if (g1.getVCenterY() < g2.getVCenterY())
				return -1;
			else
				return 1;
		}
	}

	/** sorts graphics by VCenterX */
	public static class XComparator implements Comparator<VPathwayObject> {
		public int compare(VPathwayObject g1, VPathwayObject g2) {
			if (g1.getVCenterX() == g2.getVCenterX())
				return 0;
			else if (g1.getVCenterX() < g2.getVCenterX())
				return -1;
			else
				return 1;
		}
	}

	/** sorts Graphics by ZOrder */
	public static class ZComparator implements Comparator<VPathwayObject> {
		public int compare(VPathwayObject g1, VPathwayObject g2) {
			return g1.gdata.getZOrder() - g2.gdata.getZOrder();
		}
	}

	private UndoManager undoManager = new UndoManager();

	/**
	 * Activates the undo manager by providing an engine to which the undo actions
	 * will be applied. If this method is not called, the undo manager will not
	 * record any undo actions.
	 */
	public void activateUndoManager(Engine engine) {
		undoManager.activate(engine);
	}

	/**
	 * returns undoManager owned by this instance of VPathway.
	 */
	public UndoManager getUndoManager() {
		return undoManager;
	}

	/*
	 * To be called only by undo.
	 */
	/*
	 * public void setUndoManager(UndoManager value) { undoManager = value; }
	 */
	public void undo() {
		undoManager.undo();
	}

	private boolean disposed = false;

	/**
	 * free all resources (such as listeners) held by this class. Owners of this
	 * class must explicitly dispose of it to clean up.
	 */
	public void dispose() {
		assert (!disposed);
		for (VElement elt : getDrawingObjects()) {
			elt.destroy();
		}
		cleanUp();
		if (data != null)
			data.removeListener(this);
		listeners.clear();
		selection.getListeners().clear();
		viewActions = null;
		if (parent != null)
			parent.dispose();
		parent = null; // disconnect from VPathwaySwing
		undoManager.dispose();
		undoManager = null;
		hoverManager.stop();
		disposed = true;
	}

	/**
	 * When adding elements to a pathway, they are not added immediately but placed
	 * in a temporary array. This to prevent concurrent modification of the main
	 * elements array. This method adds the elements that are scheduled to be added.
	 */
	void addScheduled() {
		for (VElement elt : toAdd) {
			if (!drawingObjects.contains(elt)) { // Don't add duplicates!
				drawingObjects.add(elt);
			}
		}
		toAdd.clear();
	}

	private void cleanUp() {
		for (Iterator<VElement> i = drawingObjects.iterator(); i.hasNext();) {
			VElement elt = i.next();
			if (elt.toBeRemoved()) {
				i.remove();
			}
		}
	}

	/**
	 * Move multiple elements together (either a group or a selection).
	 * <p>
	 * This method makes sure that elements are not moved twice if they are part of
	 * another element that is being moved. For example: If a State is moved at the
	 * same time as its parent DataNode, then the state is not moved. If a group
	 * member is moved together with the parent group, then the member is not moved.
	 */
	public void moveMultipleElements(Collection<? extends VElement> toMove, double vdx, double vdy) {
		// collect all graphIds in selection
		Set<String> eltIds = new HashSet<String>();
		// collect all groupIds in selection
		Set<String> groupIds = new HashSet<String>();
		for (VElement o : toMove) {
			if (o instanceof VPathwayObject) {
				PathwayObject elt = ((VPathwayObject) o).getPathwayObject();
				String id = elt.getElementId();
				if (id != null)
					eltIds.add(id);
				String groupId = elt.getGroupId();
				if (elt.getObjectType() == ObjectType.GROUP && groupId != null)
					groupIds.add(id);
			}
		}

		for (VElement o : toMove) {
			// skip if parent of state is also in selection.
			if (o instanceof VState && eltIds.contains(((VState) o).getPathwayObject().getElementRef()))
				continue;

			if (o instanceof VPathwayObject) {
				// skip if parent group is also in selection
				if (groupIds.contains(((VPathwayObject) o).getPathwayObject().getGroupRef()))
					continue;

				o.vMoveBy(vdx, vdy);
			}
		}
	}

}