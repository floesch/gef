/*******************************************************************************
 * Copyright (c) 2014 itemis AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Matthias Wienand (itemis AG) - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.gef4.fx.nodes;

import java.util.List;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Point2D;
import javafx.scene.Node;

import org.eclipse.gef4.fx.anchors.AnchorKey;
import org.eclipse.gef4.fx.anchors.FXChopBoxAnchor;
import org.eclipse.gef4.fx.anchors.IFXAnchor;
import org.eclipse.gef4.geometry.convert.fx.JavaFX2Geometry;
import org.eclipse.gef4.geometry.planar.Point;

public class FXChopBoxHelper {

	/*
	 * TODO: Support FXChopBoxAnchor at way points. Currently no reference
	 * points are computed for FXChopBoxAnchors at way points. The reference
	 * point for a way point could be the middle point of both neighbors.
	 */

	private MapChangeListener<AnchorKey, IFXAnchor> anchorsChangeListener = new MapChangeListener<AnchorKey, IFXAnchor>() {

		@Override
		public void onChanged(
				javafx.collections.MapChangeListener.Change<? extends AnchorKey, ? extends IFXAnchor> change) {
			if (change.getKey().equals(connection.getStartAnchorKey())) {
				// start anchor change
				if (change.getValueRemoved() != null) {
					change.getValueRemoved().positionProperty()
					.removeListener(startPCL);
				}
				if (change.getValueAdded() != null) {
					change.getValueAdded().positionProperty()
					.addListener(startPCL);
				}
			} else if (change.getKey().equals(connection.getEndAnchorKey())) {
				// end anchor key
				if (change.getValueRemoved() != null) {
					change.getValueRemoved().positionProperty()
					.removeListener(endPCL);
				}
				if (change.getValueAdded() != null) {
					change.getValueAdded().positionProperty()
					.addListener(endPCL);
				}
			} else {
				// waypoint change
				if (change.getValueRemoved() != null) {
					change.getValueRemoved().positionProperty()
					.removeListener(waypointPCL);
				}
				if (change.getValueAdded() != null) {
					change.getValueAdded().positionProperty()
					.addListener(waypointPCL);
				}
			}
		}
	};

	private IFXConnection connection;

	private MapChangeListener<? super AnchorKey, ? super Point> startPCL = new MapChangeListener<AnchorKey, Point>() {
		@Override
		public void onChanged(
				javafx.collections.MapChangeListener.Change<? extends AnchorKey, ? extends Point> change) {
			if (change.wasAdded()) {
				updateEndReferencePoint();
			}
		}
	};

	private MapChangeListener<? super AnchorKey, ? super Point> endPCL = new MapChangeListener<AnchorKey, Point>() {
		@Override
		public void onChanged(
				javafx.collections.MapChangeListener.Change<? extends AnchorKey, ? extends Point> change) {
			if (change.wasAdded()) {
				updateStartReferencePoint();
			}
		}
	};

	private MapChangeListener<AnchorKey, Point> waypointPCL = new MapChangeListener<AnchorKey, Point>() {
		@Override
		public void onChanged(
				javafx.collections.MapChangeListener.Change<? extends AnchorKey, ? extends Point> change) {
			if (change.wasAdded()) {
				updateStartReferencePoint();
				updateEndReferencePoint();
			}
		}
	};

	public FXChopBoxHelper(IFXConnection connection) {
		this.connection = connection;
		connection.anchorsProperty().addListener(
				new ChangeListener<ObservableMap<AnchorKey, IFXAnchor>>() {

					@Override
					public void changed(
							ObservableValue<? extends ObservableMap<AnchorKey, IFXAnchor>> observable,
									ObservableMap<AnchorKey, IFXAnchor> oldValue,
									ObservableMap<AnchorKey, IFXAnchor> newValue) {
						if (oldValue != null) {
							oldValue.removeListener(anchorsChangeListener);
						}
						if (newValue != null) {
							newValue.addListener(anchorsChangeListener);
						}
					}

				});
	}

	/**
	 * Returns a {@link Point} array containing reference points for the start
	 * and end anchors.
	 *
	 * @return an array of size 2 containing the reference points for the start
	 *         and end anchors
	 */
	public Point[] computeReferencePoints() {
		// find reference points
		Point startReference = null;
		Point endReference = null;
		List<Point> wayPoints = connection.getWayPoints();

		// first uncontained way point is start reference
		Node startNode = connection.getStartAnchor().getAnchorage();
		if (startNode != null) {
			for (Point p : wayPoints) {
				Point2D local = startNode.sceneToLocal(connection.getVisual()
						.localToScene(p.x, p.y));
				if (!startNode.contains(local)) {
					startReference = p;
					break;
				}
			}
		}

		// last uncontained way point is end reference
		Node endNode = connection.getEndAnchor().getAnchorage();
		if (endNode != null) {
			for (int i = wayPoints.size() - 1; i >= 0; i--) {
				Point p = wayPoints.get(i);
				Point2D local = endNode.sceneToLocal(connection.getVisual()
						.localToScene(p.x, p.y));
				if (!endNode.contains(local)) {
					endReference = p;
					break;
				}
			}
		}

		// if we did not find a startReference yet, we have to use the end
		// anchorage position or end anchor position
		if (startReference == null) {
			if (connection.isEndConnected()) {
				if (endNode != null) {
					startReference = getCenter(endNode);
				}
			}
		}
		if (startReference == null) {
			startReference = connection.getEndPoint();
		}
		if (startReference == null) {
			startReference = new Point();
		}

		// if we did not find an endReference yet, we have to use the start
		// anchorage position or start anchor position
		if (endReference == null) {
			if (connection.isStartConnected()) {
				if (startNode != null) {
					endReference = getCenter(startNode);
				}
			}
		}
		if (endReference == null) {
			endReference = connection.getStartPoint();
		}
		if (endReference == null) {
			endReference = new Point();
		}

		return new Point[] { startReference, endReference };
	}

	private Point getCenter(Node anchorageNode) {
		Point center = JavaFX2Geometry.toRectangle(
				connection.getVisual().sceneToLocal(
						anchorageNode.localToScene(anchorageNode
								.getLayoutBounds()))).getCenter();
		if (Double.isNaN(center.x) || Double.isNaN(center.y)) {
			return null;
		}
		return center;
	}

	private void updateEndReferencePoint() {
		IFXAnchor endAnchor = connection.getEndAnchor();
		if (endAnchor != null && endAnchor instanceof FXChopBoxAnchor) {
			Point[] refPoints = computeReferencePoints();
			FXChopBoxAnchor a = (FXChopBoxAnchor) endAnchor;
			AnchorKey endAnchorKey = connection.getEndAnchorKey();
			Point oldRef = a.getReferencePoint(endAnchorKey);
			if (oldRef == null || !oldRef.equals(refPoints[1])) {
				a.setReferencePoint(endAnchorKey, refPoints[1]);
			}
		}
	}

	private void updateStartReferencePoint() {
		IFXAnchor startAnchor = connection.getStartAnchor();
		if (startAnchor != null && startAnchor instanceof FXChopBoxAnchor) {
			Point[] refPoints = computeReferencePoints();
			FXChopBoxAnchor a = (FXChopBoxAnchor) startAnchor;
			AnchorKey startAnchorKey = connection.getStartAnchorKey();
			Point oldRef = a.getReferencePoint(startAnchorKey);
			if (oldRef == null || !oldRef.equals(refPoints[0])) {
				a.setReferencePoint(startAnchorKey, refPoints[0]);
			}
		}
	}

}
