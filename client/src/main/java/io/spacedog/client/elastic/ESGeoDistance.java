/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.spacedog.client.elastic;

import java.util.Locale;

/**
 * Geo distance calculation.
 */
public enum ESGeoDistance {
	/**
	 * Calculates distance as points on a plane. Faster, but less accurate than
	 * {@link #ARC}.
	 */
	PLANE,

	/**
	 * Calculates distance factor.
	 */
	FACTOR,

	/**
	 * Calculates distance as points on a globe.
	 */
	ARC,

	/**
	 * Calculates distance as points on a globe in a sloppy way. Close to the
	 * pole areas the accuracy of this function decreases.
	 */
	SLOPPY_ARC;

	/**
	 * Default {@link ESGeoDistance} function. This method should be used, If no
	 * specific function has been selected. This is an alias for
	 * <code>SLOPPY_ARC</code>
	 */
	public static final ESGeoDistance DEFAULT = SLOPPY_ARC;

	// public abstract double normalize(double distance, DistanceUnit unit);
	//
	// public abstract double calculate(double sourceLatitude, double
	// sourceLongitude, double targetLatitude, double targetLongitude,
	// DistanceUnit unit);
	//
	// public abstract FixedSourceDistance fixedSourceDistance(double
	// sourceLatitude, double sourceLongitude, DistanceUnit unit);

	// private static final double MIN_LAT = Math.toRadians(-90d); // -PI/2
	// private static final double MAX_LAT = Math.toRadians(90d); // PI/2
	// private static final double MIN_LON = Math.toRadians(-180d); // -PI
	// private static final double MAX_LON = Math.toRadians(180d); // PI

	// public static DistanceBoundingCheck distanceBoundingCheck(double
	// sourceLatitude, double sourceLongitude, double distance, DistanceUnit
	// unit) {
	// // angular distance in radians on a great circle
	// // assume worst-case: use the minor axis
	// double radDist = unit.toMeters(distance) /
	// GeoUtils.EARTH_SEMI_MINOR_AXIS;
	//
	// double radLat = Math.toRadians(sourceLatitude);
	// double radLon = Math.toRadians(sourceLongitude);
	//
	// double minLat = radLat - radDist;
	// double maxLat = radLat + radDist;
	//
	// double minLon, maxLon;
	// if (minLat > MIN_LAT && maxLat < MAX_LAT) {
	// double deltaLon = Math.asin(Math.sin(radDist) / Math.cos(radLat));
	// minLon = radLon - deltaLon;
	// if (minLon < MIN_LON) minLon += 2d * Math.PI;
	// maxLon = radLon + deltaLon;
	// if (maxLon > MAX_LON) maxLon -= 2d * Math.PI;
	// } else {
	// // a pole is within the distance
	// minLat = Math.max(minLat, MIN_LAT);
	// maxLat = Math.min(maxLat, MAX_LAT);
	// minLon = MIN_LON;
	// maxLon = MAX_LON;
	// }
	//
	// GeoPoint topLeft = new GeoPoint(Math.toDegrees(maxLat),
	// Math.toDegrees(minLon));
	// GeoPoint bottomRight = new GeoPoint(Math.toDegrees(minLat),
	// Math.toDegrees(maxLon));
	// if (minLon > maxLon) {
	// return new Meridian180DistanceBoundingCheck(topLeft, bottomRight);
	// }
	// return new SimpleDistanceBoundingCheck(topLeft, bottomRight);
	// }

	/**
	 * Get a {@link ESGeoDistance} according to a given name. Valid values are
	 * 
	 * <ul>
	 * <li><b>plane</b> for <code>GeoDistance.PLANE</code></li>
	 * <li><b>sloppy_arc</b> for <code>GeoDistance.SLOPPY_ARC</code></li>
	 * <li><b>factor</b> for <code>GeoDistance.FACTOR</code></li>
	 * <li><b>arc</b> for <code>GeoDistance.ARC</code></li>
	 * </ul>
	 * 
	 * @param name
	 *            name of the {@link ESGeoDistance}
	 * @return a {@link ESGeoDistance}
	 */
	public static ESGeoDistance fromString(String name) {
		name = name.toLowerCase(Locale.ROOT);
		if ("plane".equals(name)) {
			return PLANE;
		} else if ("arc".equals(name)) {
			return ARC;
		} else if ("sloppy_arc".equals(name)) {
			return SLOPPY_ARC;
		} else if ("factor".equals(name)) {
			return FACTOR;
		}
		throw new IllegalArgumentException("No geo distance for [" + name + "]");
	}

	public static interface FixedSourceDistance {

		double calculate(double targetLatitude, double targetLongitude);
	}

	public static interface DistanceBoundingCheck {

		boolean isWithin(double targetLatitude, double targetLongitude);

		ESGeoPoint topLeft();

		ESGeoPoint bottomRight();
	}

	public static final AlwaysDistanceBoundingCheck ALWAYS_INSTANCE = new AlwaysDistanceBoundingCheck();

	private static class AlwaysDistanceBoundingCheck implements DistanceBoundingCheck {
		@Override
		public boolean isWithin(double targetLatitude, double targetLongitude) {
			return true;
		}

		@Override
		public ESGeoPoint topLeft() {
			return null;
		}

		@Override
		public ESGeoPoint bottomRight() {
			return null;
		}
	}

	public static class Meridian180DistanceBoundingCheck implements DistanceBoundingCheck {

		private final ESGeoPoint topLeft;
		private final ESGeoPoint bottomRight;

		public Meridian180DistanceBoundingCheck(ESGeoPoint topLeft, ESGeoPoint bottomRight) {
			this.topLeft = topLeft;
			this.bottomRight = bottomRight;
		}

		@Override
		public boolean isWithin(double targetLatitude, double targetLongitude) {
			return (targetLatitude >= bottomRight.lat() && targetLatitude <= topLeft.lat())
					&& (targetLongitude >= topLeft.lon() || targetLongitude <= bottomRight.lon());
		}

		@Override
		public ESGeoPoint topLeft() {
			return topLeft;
		}

		@Override
		public ESGeoPoint bottomRight() {
			return bottomRight;
		}
	}

	public static class SimpleDistanceBoundingCheck implements DistanceBoundingCheck {
		private final ESGeoPoint topLeft;
		private final ESGeoPoint bottomRight;

		public SimpleDistanceBoundingCheck(ESGeoPoint topLeft, ESGeoPoint bottomRight) {
			this.topLeft = topLeft;
			this.bottomRight = bottomRight;
		}

		@Override
		public boolean isWithin(double targetLatitude, double targetLongitude) {
			return (targetLatitude >= bottomRight.lat() && targetLatitude <= topLeft.lat())
					&& (targetLongitude >= topLeft.lon() && targetLongitude <= bottomRight.lon());
		}

		@Override
		public ESGeoPoint topLeft() {
			return topLeft;
		}

		@Override
		public ESGeoPoint bottomRight() {
			return bottomRight;
		}
	}

	// public static class ArcFixedSourceDistance extends
	// FixedSourceDistanceBase {
	//
	// public ArcFixedSourceDistance(double sourceLatitude, double
	// sourceLongitude, DistanceUnit unit) {
	// super(sourceLatitude, sourceLongitude, unit);
	// }
	//
	// @Override
	// public double calculate(double targetLatitude, double targetLongitude) {
	// return ARC.calculate(sourceLatitude, sourceLongitude, targetLatitude,
	// targetLongitude, unit);
	// }
	//
	// }

	// public static class SloppyArcFixedSourceDistance extends
	// FixedSourceDistanceBase {
	//
	// public SloppyArcFixedSourceDistance(double sourceLatitude, double
	// sourceLongitude, DistanceUnit unit) {
	// super(sourceLatitude, sourceLongitude, unit);
	// }
	//
	// @Override
	// public double calculate(double targetLatitude, double targetLongitude) {
	// return SLOPPY_ARC.calculate(sourceLatitude, sourceLongitude,
	// targetLatitude, targetLongitude, unit);
	// }
	// }

	/**
	 * Return a {@link SortedNumericDoubleValues} instance that returns the
	 * distances to a list of geo-points for each document.
	 */
	// public static SortedNumericDoubleValues distanceValues(final
	// MultiGeoPointValues geoPointValues,
	// final FixedSourceDistance... distances) {
	// final GeoPointValues singleValues =
	// FieldData.unwrapSingleton(geoPointValues);
	// if (singleValues != null && distances.length == 1) {
	// final Bits docsWithField = FieldData.unwrapSingletonBits(geoPointValues);
	// return FieldData.singleton(new NumericDoubleValues() {
	//
	// @Override
	// public double get(int docID) {
	// if (docsWithField != null && !docsWithField.get(docID)) {
	// return 0d;
	// }
	// final GeoPoint point = singleValues.get(docID);
	// return distances[0].calculate(point.lat(), point.lon());
	// }
	//
	// }, docsWithField);
	// } else {
	// return new SortingNumericDoubleValues() {
	//
	// @Override
	// public void setDocument(int doc) {
	// geoPointValues.setDocument(doc);
	// resize(geoPointValues.count() * distances.length);
	// int valueCounter = 0;
	// for (FixedSourceDistance distance : distances) {
	// for (int i = 0; i < geoPointValues.count(); ++i) {
	// final GeoPoint point = geoPointValues.valueAt(i);
	// values[valueCounter] = distance.calculate(point.lat(), point.lon());
	// valueCounter++;
	// }
	// }
	// sort();
	// }
	// };
	// }
	// }
}
