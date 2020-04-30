package com.arcrobotics.ftclib.purepursuit;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.arcrobotics.ftclib.drivebase.MecanumDrive;
import com.arcrobotics.ftclib.geometry.Pose2d;
import com.arcrobotics.ftclib.geometry.Rotation2d;
import com.arcrobotics.ftclib.geometry.Translation2d;
import com.arcrobotics.ftclib.kinematics.Odometry;
import com.arcrobotics.ftclib.purepursuit.actions.TriggeredAction;
import com.arcrobotics.ftclib.purepursuit.types.PathType;
import com.arcrobotics.ftclib.purepursuit.types.WaypointType;
import com.arcrobotics.ftclib.purepursuit.waypoints.EndWaypoint;
import com.arcrobotics.ftclib.purepursuit.waypoints.GeneralWaypoint;
import com.arcrobotics.ftclib.purepursuit.waypoints.InterruptWaypoint;
import com.arcrobotics.ftclib.purepursuit.waypoints.PointTurnWaypoint;

/**
 * This class represents a pure pursuit path. It is used to store a path's waypoints, and do all the
 * calculation required to traverse the path. A path can be implemented in two different way. The first
 * way is running the loop/updating motor powers manually. This is a good implementation for those teams
 * who wish to just use the pure pursuit section FtcLib and not much else. The other possible implementation
 * is using the automatic feature. This features does all the hard work for you. Teams who use FtcLib's
 * features to power most of their code will find this method appealing. 
 * 
 * To learn how to implement a pure pursuit path, please see the example implementations and tutorials.
 * 
 * @see Waypoint
 * @version 1.0
 *
 */
@SuppressWarnings("serial")
public class Path extends ArrayList<Waypoint> {
	
	// This path's type (Heading controlled or waypoint ordering controlled).
	private PathType pathType;
	
	// Deceleration profile.
	private DecelerationController decelerationController;
	
	// Timeout fields. 
	private long timeoutMiliseconds;
	private long timeSinceStart;
	private Waypoint lastWaypoint;
	private long lastWaypointTimeStamp;
	private boolean timedOut;
	
	// True if automatic mode is enabled, this is disabled by default.
	private boolean autoModeEnabled;
	
	// True if retrace is enabled, this is enabled by default.
	private boolean retraceEnabled;
	
	// True if the init() method has been run.
	private boolean initComplete;
	
	// Fields for the retrace feature.
	private boolean retracing;
	private double retraceMovementSpeed;
	private double retraceTurnSpeed;
	private Translation2d lastKnownIntersection;
	
	// The robot's drive train. This is only used if automatic mode is active Right now only mecanum drives are supported.
	private MecanumDrive drivetrain;
	
	// The robot's odometry. This is only used if automatic mode is active.
	private Odometry odometry;
	
	// Action lists
	private List<TriggeredAction> triggeredActions;
	private Queue<InterruptWaypoint> interruptActionQueue;
	
	/**
	 * Constructs an empty path and sets all settings to their defaults. Use add() to add waypoints.
	 */
	public Path() {
		this(new Waypoint[] {});
	}
	
	/**
	 * Constructs a path with the given waypoint. Sets all settings to their defaults.
	 * @param waypoints Waypoints in this path.
	 */
	public Path(Waypoint... waypoints) {
		for (Waypoint waypoint : waypoints)
			add(waypoint);
		pathType = PathType.WAYPOINT_ORDERING_CONTROLLED;
		timeoutMiliseconds = -1;
		timeSinceStart = 0;
		lastWaypointTimeStamp = 0;
		retraceMovementSpeed = 1;
		retraceTurnSpeed = 1;
		autoModeEnabled = false;
		retraceEnabled = true;
		initComplete = false;
		timedOut = false;
		triggeredActions = new ArrayList<TriggeredAction>();
		interruptActionQueue = new LinkedList<InterruptWaypoint>();
		decelerationController = generateDefaultDecelerationController();
		lastWaypoint = null;
	}
	
	/**
	 * Initiates the path. This must be ran before using the path. This checks to make sure everything 
	 * in the path is valid. Please use isLegalPath() before calling this. 
	 * 
	 * In order for a path to be considered legal it must have:
	 * - At least 2 waypoints.
	 * - Begin with a StartWaypoint
	 * - End with an EndWaypoint
	 * - Not contain any StartWaypoints or EndWaypoints in it's body.
	 * 
	 * @throws IllegalStateException If the path is not legal.
	 */
	public void init() {
		// Step 1, verify that the path is valid.
		verifyLegality();
		// Step 2, check is automatic mode is enabled.
		if (autoModeEnabled) {
			// If it is enabled, check to make sure everything is configured and throw exceptions if not.
			if (drivetrain == null)
				throw new IllegalStateException("Path initiation failed. Drivetrain is not set.");
			if (odometry == null)
				throw new IllegalStateException("Path initiation failed. Odometry is not set.");
		}
		// Step 3, mark the init as complete.
		initComplete = true;
	}
	
	/**
	 * Initiates the automatic path following feature. The robot will follow the path and perform actions as configured.
	 * 
	 * The following must be configured before this method is ran:
	 * - Automode must be enabled. This can be done using the enableAutoMode() method.
	 * - The drivetrain must be set. This can be done using the setDrive() method.
	 * - The odometry must be set. This can be done using the setOdometry() method.
	 * 
	 * @return True if the false completed successfully, false if the path did not (timed out, lost path, etc.).
	 * @throws IllegalStateException If automatic mode is disabled/not configured or the init has not been ran.
	 */
	public boolean followPathAutomatically() {
		// First, make sure the init has been called. While this does not guarantee the program will run without errors, it is better than nothing.
		if (!initComplete)
			throw new IllegalStateException("You must call the init() function before calling followPathAutomatically()");
		if (autoModeEnabled) 
			throw new IllegalStateException("Automatic mode is not enabled!");
		boolean notUsingTimeout = timeoutMiliseconds == -1;
		// Next, begin the loop.
		while(!isFinished()) {
			// If the path has timed out, break the loop and return true;
			if (notUsingTimeout || System.currentTimeMillis() - timeSinceStart < timeoutMiliseconds)
				return true;
			// Get the robot's current position using the odometry.
			Pose2d robotPosition = odometry.getPose();
			// Call the loop function to get the motor powers.
			double[] motorPowers = loop(robotPosition.getTranslation().getX(), robotPosition.getTranslation().getY(), robotPosition.getHeading());
			// Update motor speeds.
			drivetrain.driveRobotCentric(motorPowers[0], motorPowers[1], motorPowers[2]);
		}
		// After the path is completed, turn off motors and return false;
		drivetrain.stopMotor();
		return false;
	}
	
	/**
	 * This is the principle path method. After everything is configured and initiated, this method can be used.
	 * Taking in the robot's x, y, and rotation, this method calculates the appropriate motor powers for the 
	 * robot to follow the path. This method also calls all triggered/interrupted actions automatically.
	 * 
	 * @param xPosition Robot's current x position.
	 * @param yPosition Robot's current y position.
	 * @param rotation Robot's current rotation.
	 * @return A double array containing the motor powers. a[0] is the x power, a[1] is the y power, and a[2] is the turn power.
	 */
	@SuppressWarnings("incomplete-switch")
	public double[] loop(double xPosition, double yPosition, double rotation) {
		// First, make sure the init has been called. While this does not guarantee the program will run without errors, it is better than nothing.
		if (!initComplete)
			throw new IllegalStateException("You must call the init() function before calling loop()");
		// Next, loop triggered and perform interrupted actions.
		loopTriggeredActions();
		runQueuedInterruptActions();
		// Get all the intersections on the path.
		ArrayList<TaggedIntersection> intersections = new ArrayList<TaggedIntersection>();
		for (int i = 1; i < size(); i++) {
			// Get the path line segment and circle.
			Translation2d linePoint1 = get(i - 1).getPose().getTranslation();
			Translation2d linePoint2 = get(i).getPose().getTranslation();
			double radius = get(i).getFollowDistance();
			Translation2d robotPosition = new Translation2d(xPosition, yPosition);
			List<Translation2d> points = PurePursuitUtil.lineCircleIntersection(robotPosition, radius, linePoint1, linePoint2);
			for (Translation2d point : points)
				// Add results to list.
				intersections.add(new TaggedIntersection(point, get(i), i));
			if (get(i) instanceof PointTurnWaypoint) {
				// If the second waypoint is a point turn waypoint, decrease the follow radius so the next point is always found.
				double dx = xPosition - linePoint2.getX();
				double dy = yPosition - linePoint2.getY();
				double adjustedRadius = Math.hypot(dx, dy) - 0.01;
				if (adjustedRadius < radius) {
					List<Translation2d> endPoints = PurePursuitUtil.lineCircleIntersection(robotPosition, adjustedRadius, linePoint1, linePoint2);
					for (Translation2d point : endPoints)
						// Add results to list.
						intersections.add(new TaggedIntersection(point, get(i), i));
				}
			}
			// Now all intersections are recorded.
		}
		// If there are no intersections found, the path is lost.
		if (intersections.size() == 0) {
			if (retracing)
				return retrace(xPosition, yPosition, rotation);
			// If retrace is enabled, we can try to re-find the path.
			if (retraceEnabled) { 
				if (lastKnownIntersection == null)
					lastKnownIntersection = get(0).getPose().getTranslation();
				retracing = true;
				return retrace(xPosition, yPosition, rotation);
			} else 
				return new double[] {0, 0, 0};
		} else
			retracing = false;
		// The intersections are handled differently depending on the path type.
		TaggedIntersection bestIntersection = intersections.get(0);
		switch(pathType) {
		case HEADING_CONTROLLED:
			bestIntersection = selectHeadingControlledIntersection(intersections, new Pose2d(xPosition, yPosition, new Rotation2d(rotation)));
			break;
		case WAYPOINT_ORDERING_CONTROLLED:
			bestIntersection = selectWaypointOrderingControlledIntersection(intersections);
			break;
		}
		if (retraceEnabled)
			// If retrace is enabled, store the intersection.
			lastKnownIntersection = bestIntersection.intersection;
		if (bestIntersection.taggedPoint != lastWaypoint) {
			// If this is the first intersection of a new waypoint, update timeout values.
			lastWaypoint = bestIntersection.taggedPoint;
			lastWaypointTimeStamp = System.currentTimeMillis();
		}
		if (bestIntersection.taggedPoint.getTimeout() != -1)
			// If this waypoint has a timeout, make sure it hasn't timed out.
			if (System.currentTimeMillis() > lastWaypointTimeStamp + bestIntersection.taggedPoint.getTimeout())
				// If it has, return null.
				return null;
		// After the best intersection is found, the robot behaves differently depending on the type of waypoint.
		double[] motorPowers = new double[] {0, 0, 0};
		switch(bestIntersection.taggedPoint.getType()) {
		case GENERAL:
			motorPowers = handleGeneralIntersection(bestIntersection, new Pose2d(xPosition, yPosition, new Rotation2d(rotation)));
			break;
		case POINT_TURN:
			motorPowers = handlePointTurnIntersection(bestIntersection, new Pose2d(xPosition, yPosition, new Rotation2d(rotation)));
			break;
		case INTERRUPT:
			motorPowers = handleInterruptIntersection(bestIntersection, new Pose2d(xPosition, yPosition, new Rotation2d(rotation)));
			break;
		case END:
			motorPowers = handleEndIntersection(bestIntersection, new Pose2d(xPosition, yPosition, new Rotation2d(rotation)));
			break;
		}
		// Return the motor powers.
		return motorPowers;
	}
	
	/**
	 * Retraces the robot's moves back to the path's last known location. 
	 * 
	 * @param xPosition Robot's x position.
	 * @param yPosition Robot's y position.
	 * @param rotation Robot's rotation.
	 * @return A double array containing the motor powers. a[0] is the x power, a[1] is the y power, and a[2] is the turn power.
	 */
	private double[] retrace(double xPosition, double yPosition, double rotation) {
		// Move towards the last known intersection.
		double[] motorPowers = PurePursuitUtil.moveToPosition(xPosition, yPosition, rotation, lastKnownIntersection.getX(), lastKnownIntersection.getY(), rotation, false);
		motorPowers[0] *= retraceMovementSpeed;
		motorPowers[1] *= retraceMovementSpeed;
		motorPowers[2] *= retraceTurnSpeed;
		return motorPowers;
	}
	
	/**
	 * Selects and returns the "best" intersection from the given list using heading 
	 * control. The intersection is chosen based on the following rules:
	 * 1. If the list contains any untraversed waypoints, they are given priority and the best intersection is the point closest to the point turn waypoint.
	 * 2. If the list contains no point turn points, then it chooses the intersection the robot is oriented most closely towards. 
	 * 
	 * @param intersections Intersection list.
	 * @param robotPos Robot's current position/rotation.
	 * @return The best intersection in the form of a TaggedIntersection.
	 */
	private TaggedIntersection selectHeadingControlledIntersection(List<TaggedIntersection> intersections, Pose2d robotPos) {
		TaggedIntersection bestIntersection = intersections.get(0);
		boolean pointTurnPriority = false;
		/**
		 * In a heading controlled path, the intersection the robot is most closely oriented toward is considered the "best point".
		 */
		for (TaggedIntersection intersection : intersections) {
			// Check to see if a point turn waypoint is found.
			if (intersection.taggedPoint instanceof PointTurnWaypoint) {
				PointTurnWaypoint ptwaypoint = (PointTurnWaypoint) intersection.taggedPoint;
				if (!ptwaypoint.hasTraversed()) {
					// If point turn waypoint is found, and it has not already been traversed, then it takes priority.
					pointTurnPriority = true;
					if (!(bestIntersection.taggedPoint instanceof PointTurnWaypoint)) 
						bestIntersection = intersection;
					else {
						// If two intersections associated with a point turn waypoint are found, choose the one closer to the waypoint.
						if (bestIntersection.waypointIndex < intersection.waypointIndex)
							// If the intersection is obviously behind.
							bestIntersection = intersection;
						else if (bestIntersection.waypointIndex == intersection.waypointIndex)
							// Check to see if it is in front.
							if (PurePursuitUtil.isInFront(get(intersection.waypointIndex - 1).getPose().getTranslation(), intersection.taggedPoint.getPose().getTranslation(), intersection.intersection, bestIntersection.intersection))
								bestIntersection = intersection;
					}
				}
			} else if (pointTurnPriority)
				// If pointTurnPriority is active, ignore non point turn intersections.
				continue;
			else {
				// Normal case.
				// Relative angle to intersection.
				double absoluteAngleToIntersection = Math.atan2(intersection.intersection.getY(), intersection.intersection.getX());
				double relativeAngleToIntersection = absoluteAngleToIntersection - robotPos.getHeading();
				// Relative angle to best intersection.
				double absoluteAngleToBestIntersection = Math.atan2(bestIntersection.intersection.getY(), bestIntersection.intersection.getX());
				double relativeAngleToBestIntersection = absoluteAngleToBestIntersection - robotPos.getHeading();
				if (relativeAngleToIntersection < relativeAngleToBestIntersection)
					// Update bestIntersection.
					bestIntersection = intersection;
			}
		}
		// Return the best intersection.
		return bestIntersection;
	}
	
	/**
	 * Selects and returns the "best" intersection from the given list by choosing the intersection that is farthest along the path.
	 * The intersection is chosen based on the following rules:
	 * 1. If the list contains any untraversed waypoints, they are given priority and the best intersection is the point closest to the point turn waypoint.
	 * 2. If the list contains no point turn points, then it chooses the intersection that is farthest along the path. 
	 * 
	 * @param intersections Intersection list.
	 * @param robotPos Robot's current position/rotation.
	 * @return The best intersection in the form of a TaggedIntersection.
	 */
	private TaggedIntersection selectWaypointOrderingControlledIntersection(List<TaggedIntersection> intersections) {
		TaggedIntersection bestIntersection = intersections.get(0);
		boolean pointTurnPriority = false;
		/**
		 * In a waypoint ordering controlled path, the intersection that is farthest along the path is considered the "best point".
		 */
		for (TaggedIntersection intersection : intersections) {
			// Check to see if a point turn waypoint is found.
			if (intersection.taggedPoint instanceof PointTurnWaypoint) {
				PointTurnWaypoint ptwaypoint = (PointTurnWaypoint) intersection.taggedPoint;
				if (!ptwaypoint.hasTraversed()) {
					// If point turn waypoint is found, and it has not already been traversed, then it takes priority.
					pointTurnPriority = true;
					if (!(bestIntersection.taggedPoint instanceof PointTurnWaypoint)) 
						bestIntersection = intersection;
					else {
						// If two intersections associated with a point turn waypoint are found, choose the one closer to the waypoint.
						if (bestIntersection.waypointIndex < intersection.waypointIndex)
							// If the intersection is obviously behind.
							bestIntersection = intersection;
						else if (bestIntersection.waypointIndex == intersection.waypointIndex)
							// Check to see if it is in front.
							if (PurePursuitUtil.isInFront(get(intersection.waypointIndex - 1).getPose().getTranslation(), intersection.taggedPoint.getPose().getTranslation(), intersection.intersection, bestIntersection.intersection))
								bestIntersection = intersection;
					}
				}
			} else if (pointTurnPriority)
				// If pointTurnPriority is active, ignore non point turn intersections.
				continue;
			else {
				// Normal case.
				if (bestIntersection.waypointIndex < intersection.waypointIndex)
					// If the intersection is obviously ahead.
					bestIntersection = intersection;
				else if (bestIntersection.waypointIndex == intersection.waypointIndex)
					// Check to see if it is in front.
					if (PurePursuitUtil.isInFront(get(intersection.waypointIndex - 1).getPose().getTranslation(), intersection.taggedPoint.getPose().getTranslation(), intersection.intersection, bestIntersection.intersection))
						bestIntersection = intersection;
			}
		}
		// Return the best intersection.
		return bestIntersection;
	}
	
	/**
	 * Returns the motor speeds required to approach the given intersection.
	 * 
	 * @param intersection Intersection to approach. 
	 * @param robotPos Robot's current position/rotation.
	 * @return Final motor speeds.
	 */
	private double[] handleGeneralIntersection(TaggedIntersection intersection, Pose2d robotPos) {
		/**
		 * General intersections are handled like normal pure pursuit intersections. The robot simply moves towards them.
		 */
		GeneralWaypoint waypoint = (GeneralWaypoint) intersection.taggedPoint;
		// Get necessary values.
		double cx = robotPos.getTranslation().getX();
		double cy = robotPos.getTranslation().getY();
		double ca = robotPos.getHeading();
		double tx = intersection.intersection.getX();
		double ty = intersection.intersection.getY();
		double ta;
		if (waypoint.usingPreferredAngle())
			// If this waypoint has a preferred angle, use it instead of the calculated angle.
			ta = (waypoint.getPreferredAngle());
		else
			// Calculate the target angle.
			ta = Math.atan2(ty - cy, tx - cx);
		// Get raw motor powers.
		double[] motorPowers = PurePursuitUtil.moveToPosition(cx, cy, ca, tx, ty, ta, false);
		// Adjust speed.
		motorPowers[0] *= waypoint.getMovementSpeed();
		motorPowers[1] *= waypoint.getMovementSpeed();
		motorPowers[2] *= waypoint.getTurnSpeed();
		return motorPowers;
	}
	
	/**
	 * Returns the motor speeds required to approach the given point turn intersection. 
	 * This will cause the robot to behave as follows:
	 * 1. Approach and decelerate to the waypoint.
	 * 2. Perform a point turn.
	 * 3. Continue to the next waypoint as normal.
	 * 
	 * @param intersection Intersection to approach. 
	 * @param robotPos Robot's current position/rotation.
	 * @return Final motor speeds.
	 */
	private double[] handlePointTurnIntersection(TaggedIntersection intersection, Pose2d robotPos) {
		/**
		 * Point turn intersections are handled very differently than general intersections. Instead of "curving" around
		 * the point, the robot will decelerate and perform a point turn.
		 */
		PointTurnWaypoint waypoint = (PointTurnWaypoint) intersection.taggedPoint;
		// Get necessary values.
		double cx = robotPos.getTranslation().getX();
		double cy = robotPos.getTranslation().getY();
		double ca = robotPos.getHeading();
		double tx = intersection.taggedPoint.getPose().getTranslation().getX();
		double ty = intersection.taggedPoint.getPose().getTranslation().getY();
		double ta;
		double[] motorPowers;
		if (!waypoint.hasTraversed() && PurePursuitUtil.positionEqualsWithBuffer(robotPos.getTranslation(), waypoint.getTranslation(), waypoint.getPositionBuffer())) {
			// If the robot has not reached the point.
			if (((GeneralWaypoint) get(intersection.waypointIndex + 1)).usingPreferredAngle()) {
				if (PurePursuitUtil.rotationEqualsWithBuffer(robotPos.getHeading(), ((GeneralWaypoint) get(intersection.waypointIndex + 1)).getPreferredAngle(), waypoint.getRotationBuffer()))
					// If the robot has reached the point and is at the preferredAngle, then the point is traversed.
					waypoint.setTraversed();
				// Set the target angle.
				ta = ((GeneralWaypoint) get(intersection.waypointIndex + 1)).getPreferredAngle();
			} else {
				double tempTy = ((GeneralWaypoint) get(intersection.waypointIndex + 1)).getPose().getTranslation().getY();
				double tempTx = ((GeneralWaypoint) get(intersection.waypointIndex + 1)).getPose().getTranslation().getX();
				// Calculate the target angle.
				ta = Math.atan2(tempTy - cy, tempTx - cx);
				if (PurePursuitUtil.rotationEqualsWithBuffer(robotPos.getHeading(), ta, waypoint.getRotationBuffer()))
					// If the robot has reached the point and is at the target angle, then the point is traversed.
					waypoint.setTraversed();
			}
			motorPowers = PurePursuitUtil.moveToPosition(cx, cy, ca, tx, ty, ta, true);
		} else {
			if (waypoint.usingPreferredAngle())
				// If this waypoint has a preferred angle, use it instead of the calculated angle.
				ta = (waypoint.getPreferredAngle());
			else
				// Calculate the target angle.
				ta = Math.atan2(ty - cy, tx - cx);
			motorPowers = PurePursuitUtil.moveToPosition(cx, cy, ca, tx, ty, ta, false);
		}
		// Manage motor speeds.
		decelerationController.process(motorPowers, Math.hypot(tx - cx, ty - cy), waypoint.getMovementSpeed(), waypoint.getTurnSpeed());
		return motorPowers;
	}
	
	/**
	 * Returns the motor speeds required to approach the given interrupt intersection. 
	 * This will cause the robot to behave as follows:
	 * 1. Approach and decelerate to the waypoint.
	 * 2. Perform a point turn / align with the preferred angle.
	 * 4. Perform the interrupt action.
	 * 3. Continue to the next waypoint as normal.
	 * 
	 * @param intersection Intersection to approach. 
	 * @param robotPos Robot's current position/rotation.
	 * @return Final motor speeds.
	 */
	private double[] handleInterruptIntersection(TaggedIntersection intersection, Pose2d robotPos) {
		/**
		 * Interrupt intersections are handled similarly to point turn intersections. Instead of continuing directly
		 * after it has turned, the robot will stop and perform the interrupt actions.
		 */
		InterruptWaypoint waypoint = (InterruptWaypoint) intersection.taggedPoint;
		// Get necessary values.
		double cx = robotPos.getTranslation().getX();
		double cy = robotPos.getTranslation().getY();
		double ca = robotPos.getHeading();
		double tx = intersection.taggedPoint.getPose().getTranslation().getX();
		double ty = intersection.taggedPoint.getPose().getTranslation().getY();
		double ta;
		double[] motorPowers;
		if (!waypoint.hasTraversed() && PurePursuitUtil.positionEqualsWithBuffer(robotPos.getTranslation(), waypoint.getTranslation(), waypoint.getPositionBuffer())) {
			// If the robot has not reached the point.
			if (((GeneralWaypoint) get(intersection.waypointIndex + 1)).usingPreferredAngle()) {
				if (PurePursuitUtil.rotationEqualsWithBuffer(robotPos.getHeading(), ((GeneralWaypoint) get(intersection.waypointIndex + 1)).getPreferredAngle(), waypoint.getRotationBuffer())) {
					// If the robot has reached the point and is at the preferredAngle, then the point is traversed.
					waypoint.setTraversed();
					// Queue the action.
					interruptActionQueue.add(waypoint);
					// Stop the robot while it does the action.
					return new double[] {0, 0, 0};
					}
				// Set the target angle.
				ta = ((GeneralWaypoint) get(intersection.waypointIndex + 1)).getPreferredAngle();
			} else {
				double tempTy = ((GeneralWaypoint) get(intersection.waypointIndex + 1)).getPose().getTranslation().getY();
				double tempTx = ((GeneralWaypoint) get(intersection.waypointIndex + 1)).getPose().getTranslation().getX();
				// Calculate the target angle.
				ta = Math.atan2(tempTy - cy, tempTx - cx);
				if (PurePursuitUtil.rotationEqualsWithBuffer(robotPos.getHeading(), ta, waypoint.getRotationBuffer())) {
					// If the robot has reached the point and is at the target angle, then the point is traversed.
					waypoint.setTraversed();
					// Queue the action.
					interruptActionQueue.add(waypoint);
					// Stop the robot while it does the action.
					return new double[] {0, 0, 0};
				}
			}
			motorPowers = PurePursuitUtil.moveToPosition(cx, cy, ca, tx, ty, ta, true);
		} else {
			if (waypoint.usingPreferredAngle())
				// If this waypoint has a preferred angle, use it instead of the calculated angle.
				ta = (waypoint.getPreferredAngle());
			else
				// Calculate the target angle.
				ta = Math.atan2(ty - cy, tx - cx);
			motorPowers = PurePursuitUtil.moveToPosition(cx, cy, ca, tx, ty, ta, false);
		}
		// Manage motor speeds.
		decelerationController.process(motorPowers, Math.hypot(tx - cx, ty - cy), waypoint.getMovementSpeed(), waypoint.getTurnSpeed());
		return motorPowers;
	}
	
	/**
	 * Returns the motor speeds required to approach the given end intersection. 
	 * This will cause the robot to behave as follows:
	 * 1. Approach and decelerate to the end point.
	 * 2. Turn to face the preferred angle (if provided).
	 * 3. Mark the path as complete.
	 * 
	 * @param intersection Intersection to approach. 
	 * @param robotPos Robot's current position/rotation.
	 * @return Final motor speeds.
	 */
	private double[] handleEndIntersection(TaggedIntersection intersection, Pose2d robotPos) {
		/**
		 * End intersections are handled the same way as interrupt intersections.
		 */
		return handleInterruptIntersection(intersection, robotPos);
	}
	
	/**
	 * Sets a timeout for the entire path. If the path does not complete within the timeout period, it 
	 * will abort. This is an optional feature.
	 * @param timeoutMiliseconds Timeout to be set.
	 */
	public void setPathTimeout(long timeoutMiliseconds) {
		this.timeoutMiliseconds = timeoutMiliseconds;
	}
	
	/**
	 * Sets the path type to the specified type. By default the path type is WAYPOINT_ORDERING_CONTROLLED.
	 * @param pathType Path type to be set.
	 */
	public void setPathType(PathType pathType) {
		this.pathType = pathType;
	}
	
	/**
	 * Sets the DecelerationController controller to the provided controller.
	 * @param controller Controllers to be set.
	 * @throws NullPointerException If the controller is null.
	 */
	public void setDecelerationController(DecelerationController controller) {
		if (controller == null)
			throw new NullPointerException("The deceleration controller connot be null");
		decelerationController = controller;
	}
	
	/**
	 * Sets the timeouts of n waypoints where n is the amount of arguments provided. 
	 * The nth waypoint timeout is set the the nth argument given.
	 * @param timeouts Timeouts to be set.
	 */
	public void setWaypointTimeouts(long... timeouts) {
		for (int i = 0; i < size() && i < timeouts.length; i++)
			get(i).setTimeout(timeouts[i]);
	}
	
	/**
	 * Sets the timeout for each individual waypoint to be the value provided. This is not recommended.
	 * @param timeout Universal timeout to be set.
	 */
	public void setWaypointTimeouts(long timeout) {
		for (Waypoint waypoint : this)
			waypoint.setTimeout(timeout);
	}
	
	/**
	 * Sets the drive train to the provided mecanum drive. This is only necessary if you are using automatic mode.
	 * Note: automatic mode currently only supports mecanum drives.
	 * @param drivetrain MecanumDrive to be set.
	 */
	public void setDrive(MecanumDrive drivetrain) {
		this.drivetrain = drivetrain;
	}
	
	/**
	 * Sets the odometry. This is only necessary if you are using automatic mode.
	 * @param odometry Odometry to be set.
	 */
	public void setOdometry(Odometry odometry) {
		this.odometry = odometry;
	}
	
	/**
	 * Configures the retrace settings. The default values are as follows:
	 * movementSpeed = 1
	 * turnSpeed = 1
	 * @param movementSpeed Movement speed to be set.
	 * @param turnSpeed Turn speed to be set.
	 */
	public void setRetraceSettings(double movementSpeed, double turnSpeed) {
		retraceMovementSpeed = normalizeSpeed(movementSpeed);
		retraceTurnSpeed = normalizeSpeed(turnSpeed);
	}
	
	/**
	 * Resets all timeouts. 
	 */
	public void resetTimeouts() {
		timedOut = false;
		lastWaypointTimeStamp = System.currentTimeMillis();
	}
	
	/**
	 * Enables automatic mode. This mode is designed for teams that are already using many features of FtcLib.
	 * In this mode, the robot will automatically follow the path and perform configured actions. See documentation 
	 * to learn how to setup automatic mode.
	 */
	public void enableAutoMode() {
		autoModeEnabled = true;
	}
	
	/**
	 * Disables automatic mode. Automatic mode is disabled by default.
	 */
	public void disableAutoMode() {
		autoModeEnabled = false;
	}
	
	/**
	 * Enables retrace. If the robot loses the path and this is enabled, the robot will retrace its moves to try
	 * to re find the path. This is enabled by default.
	 */
	public void enableRetrace() {
		retraceEnabled = true;
	}
	
	/**
	 * Disables retrace.
	 */
	public void disableRetrace() {
		retraceEnabled = false;
	}
	
	/**
	 * Adds the provided TriggeredActions to the path. These are handled automatically.
	 * @param actions TriggeredActions to be added.
	 */
	public void addTriggeredActions(TriggeredAction... actions) {
		for (TriggeredAction triggeredAction : actions)
			triggeredActions.add(triggeredAction);
	}
	
	/**
	 * Removes the first instance of the provided TriggeredAction from the path. 
	 * @param action TriggeredAction to be removed
	 * @return True if the action was found and removed, false otherwise.
	 */
	public boolean removeTriggeredAction(TriggeredAction action) {
		return triggeredActions.remove(action);
	}
	
	/**
	 * Removes all TriggeredActions from the path.
	 */
	public void clearTriggeredActions() {
		triggeredActions.clear();
	}
	
	/**
	 * Returns true if this path is legal, false otherwise.
	 * 
	 * In order for a path to be considered legal it must have:
	 * - At least 2 waypoints.
	 * - Begin with a StartWaypoint
	 * - End with an EndWaypoint
	 * - Not contain any StartWaypoints or EndWaypoints in it's body.
	 * 
	 * @return true if this path is legal, false otherwise.
	 */
	public boolean isLegalPath() {
		try {
			verifyLegality();
			initComplete = false;
		} catch(IllegalStateException e) {
			return false;
		}
		return true;
	}
	
	/**
	 * Returns true if the path has been completed, false otherwise.
	 * @return true if the path has been completed, false otherwise.
	 */
	public boolean isFinished() {
		if (size() > 0 && get(size() - 1).getType() == WaypointType.END)
			return ((EndWaypoint) get(size() - 1)).isFinished();
		return false;
	}
	
	/**
	 * Returns true if this path has timed out, false otherwise.
	 * @return true if this path has timed out, false otherwise.
	 */
	public boolean timedOut() {
		return timedOut;
	}
	
	/**
	 * Normalizes the given raw speed to be in the range [0, 1]
	 * @param raw Raw speed value to be normalized.
	 * @return Normalized speed value.
	 */
	protected double normalizeSpeed(double raw) {
		if (raw > 1)
			return 1;
		if (raw < 0)
			return 0;
		return raw;
	}
	
	/**
	 * Checks to make sure the path is legal. If it is not, an exception is thrown.
	 * 
	 * In order for a path to be considered legal it must have:
	 * - At least 2 waypoints.
	 * - Begin with a StartWaypoint
	 * - End with an EndWaypoint
	 * - Not contain any StartWaypoints or EndWaypoints in it's body.
	 * 
	 * @throws IllegalStateException If the path is not legal.
	 */
	private void verifyLegality() {
		if (size() < 2)
			throw new IllegalStateException("A path must have at least two waypoints.");
		if (get(0).getType() != WaypointType.START)
			throw new IllegalStateException("A path must start with a StartWaypoint.");
		if (get(size() - 1).getType() != WaypointType.END)
			throw new IllegalStateException("A path must end with an EndWaypoint.");
		for (int i = 1; i < size() - 1; i++) 
			if (get(i).getType() == WaypointType.END || get(i).getType() == WaypointType.START)
				throw new IllegalStateException("A path must not have end and start waypoints anywhere other than the first and last spot.");
	}
	
	/**
	 * Calls the loop() method on all TriggeredActions in this path.
	 */
	private void loopTriggeredActions() {
		for (TriggeredAction action : triggeredActions)
			action.loop();
	}
	
	/**
	 * Performs all queued interrupt actions.
	 */
	private void runQueuedInterruptActions() {
		while(!interruptActionQueue.isEmpty())
			interruptActionQueue.remove().performAction();;
	}
	
	/**
	 * Generates and returns the default DecelerationController.
	 * @return the default DecelerationController.
	 */
	private DecelerationController generateDefaultDecelerationController() {
		return new DecelerationController() {
			@Override
			public void decelerateMotorSpeeds(double[] motorSpeeds, double distanceToTarget, double lastDistanceToTarget, long timeSinceLastCallNano, double configuredMovementSpeed, double configuredTurnSpeed) {
				// This is the default deceleration profile. In the future versions, this will implement a smarter method.
				motorSpeeds[0] *= configuredMovementSpeed;
				motorSpeeds[1] *= configuredMovementSpeed;
				motorSpeeds[2] *= configuredTurnSpeed;
			}
		};
	}
	
	/**
	 * This private class is used to store additional information associated with an intersection.
	 * 
	 * @version 1.0
	 *
	 */
	private static class TaggedIntersection {
		
		// Location of the intersection.
		public Translation2d intersection;
		
		// Waypoint associated with the intersection.
		public Waypoint taggedPoint;
		
		// The associated waypoint's index in the path.
		public int waypointIndex;
		
		/**
		 * Constructs a TaggedIntersection object with the given values.
		 * @param intersection Location of the intersection.
		 * @param taggedPoint Waypoint associated with the intersection.
		 * @param waypointIndex The associated waypoint's index in the path.
		 */
		public TaggedIntersection(Translation2d intersection, Waypoint taggedPoint, int waypointIndex) {
			this.intersection = intersection;
			this.taggedPoint = taggedPoint;
			this.waypointIndex = waypointIndex;
		}
		
	}
	
}
