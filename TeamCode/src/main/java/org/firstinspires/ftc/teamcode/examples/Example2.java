import com.arcrobotics.ftclib.drivebase.MecanumDrive;
import com.arcrobotics.ftclib.kinematics.Odometry;
import com.arcrobotics.ftclib.purepursuit.Path;
import com.arcrobotics.ftclib.purepursuit.Waypoint;
import com.arcrobotics.ftclib.purepursuit.actions.InterruptAction;
import com.arcrobotics.ftclib.purepursuit.waypoints.EndWaypoint;
import com.arcrobotics.ftclib.purepursuit.waypoints.GeneralWaypoint;
import com.arcrobotics.ftclib.purepursuit.waypoints.InterruptWaypoint;
import com.arcrobotics.ftclib.purepursuit.waypoints.PointTurnWaypoint;
import com.arcrobotics.ftclib.purepursuit.waypoints.StartWaypoint;

public class Example2 {

	public void runOpMode() {
		
		/*
		 * Alternate implementation for those who wish to use the entire FtcLib eco-system on their robot.
		 */
		
		// Pure Pursuit Path is declared here.
		Waypoint p1 = new StartWaypoint(0, 0); 
		Waypoint p2 = new GeneralWaypoint(1, 1, 0, 0.5, 0.5, 2); 
		Waypoint p3 = new GeneralWaypoint(2, 2, 0, 1, 1, 2);
		Waypoint p4 = new GeneralWaypoint(2, 3, 0, 1, 2, 1);
		Waypoint p5 = new InterruptWaypoint(2, 2, 0, 1, 1, 2, 0.1, 0.1, new InterruptAction() { 
			@Override
			public void doAction() {
				grabBlock();
			}
		});
		Waypoint p6 = new GeneralWaypoint(3, 3, 0, 1, 1, 2);
		Waypoint p7 = new PointTurnWaypoint(4, 5, 2, 1, 2, 1, 0.1, 0.1); 
		Waypoint p8 = new EndWaypoint(8, 8, 0, 1, 1, 2, 0.1, 0.1);
		
		// Add all points to a path.
		Path path = new Path(p1, p2, p3, p4, p5, p6, p7, p8);
		
		// Enable the automatic pathing mode.
		path.enableAutoMode();
		
		path.setPathTimeout(30000); // Timeout for the entire path.
		path.setWaypointTimeouts(6000, 6000, 6000, 2000, 6000, 7000, 10000, 800, 2000); // Timeouts for each waypoint.
		
		path.enableRetrace();
		path.setRetraceSettings(0.7, 0.7); 
		
		path.setDrive(mecanumDrive); // Set the drive base of the robot.
		path.setOdometry(odometry); // Set the odometry of the robot.
		
		// isLegalPath() makes sure the path is valid. 
		if (path.isLegalPath())
			path.init(); // Init the path (Makes sure everything is in order and throws exceptions if not).
		
		waitForStart();
		
		// Stuff before here
		
		path.followPathAutomatically();
		
		// Stuff after here
		
	}
	
	
	
	
	
	
	
	// Placeholders
	
	public void updateMotorPowers(double x, double y, double turn) {}
	
	public void waitForStart() {}
	
	public boolean opModeIsActive() { return true; }
	
	public void grabBlock() {}
	
	private Odometry odometry = null;
	private MecanumDrive mecanumDrive = null;
	
	
}
