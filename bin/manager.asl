// Agent: Manager
/* Initial beliefs */
waiting_requests([]).  // Initialize an empty list of requests

/* Initial goal */
!manage_environment.

/* Main goal: manage environment */
+!manage_environment
   <- .print("Manager waiting for requests...");
      !listen_for_requests.

/* Listen for collision reports */
+!listen_for_requests 
   <- .wait(2000);
      ?waiting_requests(L);  // Check for requests
      .print("Waiting requests length: ", .length(L));
      !process_requests(L).

+!process_requests(L) : .length(L) >= 1
   <- !resolve_collisions(L).
+!process_requests(L).

+collision(AgentId, X, Y)
   <- .print("Collision detected for agent ", AgentId, " at location (", X, ", ", Y, ")");
      ?waiting_requests(L);
      -+waiting_requests([collision(AgentId, X, Y) | L]);  // Add the new collision request to the list
      !listen_for_requests.

/* Resolve collisions and inform agents */
+!resolve_collisions([collision(Agent1, X1, Y1), collision(Agent2, X2, Y2) | Rest])
   <- .print("Resolving collisions for agents: ", Agent1, " and ", Agent2);
      resolve_collision;
      -+waiting_requests(Rest);  // Remove the processed requests
      !listen_for_requests.

+!resolve_collisions([collision(Agent1, X1, Y1)])
   <- .print("Resolving collision for agent: ", Agent1);
      resolve_collision;
      -+waiting_requests([]);  // Remove the processed request
      !listen_for_requests.

+!resolve_collisions.
