package cacheperf.comparisons.gemfirexd.useCase6;

import hydra.Log;
import hydra.*;
import hydra.blackboard.Blackboard;
import hydra.blackboard.SharedMap;

import java.util.*;

/**
 * Created by swati on 20/4/16.
 */
public class UseCase6BB extends Blackboard {

    // Blackboard variables
    static String BB_NAME = "UseCase13_Blackboard";
    static String BB_TYPE = "RMI";

    public static UseCase6BB bbInstance = null;

    // Counter to allow 'killer' thread to know how much work we've done
    public static int numWorkLoadGenerated;
    public static int numServersStarted;
    public static int serverStarted;
    public static int rebalanceDone;
    public static int numServersInBB;
    public static int triggerDataGenerationTask;

    // SharedMap key for Task Status
    public static String startFabricServerTask_Status = "startServer";

    public static String numThreadsInStartStoreTask = "numThreadsInStartStoreTask";
//    public static Set<Integer> startFabricServerTask_threadID = new LinkedHashSet<Integer>();
    /**
     *  Get the BB instance
     */
    public static UseCase6BB getBB() {
        if (bbInstance == null) {
            synchronized ( UseCase6BB.class ) {
                if (bbInstance == null)
                    bbInstance = new UseCase6BB(BB_NAME, BB_TYPE);
            }
        }
//        Log.getLogWriter().info("startFabricServerTask_Status is ::" + startFabricServerTask_Status);
        return bbInstance;
    }

    /**
     *  Zero-arg constructor for remote method invocations.
     */
    public UseCase6BB() {
    }

    /**
     *  Creates a sample blackboard using the specified name and transport type.
     */
    public UseCase6BB(String name, String type) {
        super(name, type, UseCase6BB.class);
    }

}
