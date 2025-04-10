// Agent: Explorer1

/* Initial beliefs */
steps_remaining(0).
moves_remaining(0).
done :- done(true).
hasCollision :- isCollision(true).

/* Initial goal */
!maximize_points.

/* Plans */

// Main goal: maximize points
+!maximize_points
   <- !get_moves; // Get moves left
      .wait(2000);
      !find_target. // Start collecting rewards


+!get_moves
   <- .print("Agent1 requesting moves from the environment...");
      get_moves;  // Call the environment action
      ?moves(N);  // Wait for the environment to provide moves(N)
      .print("Moves received: ", N);
      -+moves_remaining(N).  // Update moves_remaining


// Sometimes the steps percept doesn't get updated and I don't know why
+steps(S)
   <- .print("Steps received: ", S);
      -+steps_remaining(S).  // Update steps_remaining

+!get_steps
   <- .print("Agent0 requesting steps from the environment...");
      .abolish(steps_remaining(_));
      get_steps.


// Plan to collect rewards using A* search
+!find_target : moves_remaining(N) & N = 0
   <- .print("No moves remaining. Explorer1 will stop.").

+!find_target : moves_remaining(N) & N > 0
   <- algorithm(1);  // Use A* algorithm in Java to find the best target
      !get_steps;
      !move.


// Manages the movement of explorer toward a specific location
// First check if we've reached the target
+!move : steps_remaining(S) & S = 0
   <- .print("Explorer1 has reached the target. Collecting reward...");
      collect(1);
      !maximize_points.

// Then check if there's a collision
+!move : hasCollision & moves_remaining(N) & N > 0 & steps_remaining(S) & S > 0 & N > S
   <- .print("Collision detected! Communicating with manager...");
      !resolve_collision.

// Then check if we have enough moves
+!move : moves_remaining(N) & N > 0 & steps_remaining(S) & S > 0 & N < S
   <- .print("Explorer1 doesn't have enough moves to reach target. Explorer1 will stop.").

// Finally, if all conditions are good, move
+!move : moves_remaining(N) & N > 0 & steps_remaining(S) & S > 0 & N >= S & not hasCollision 
   <- .print("Explorer1 moving towards target with steps ", S, " and remaining moves: ", N);
      move_towards(1);
      -+moves_remaining(N-1);
      -+steps_remaining(S-1);
      !move.


// Resolve collision by communicating with manager
+!resolve_collision
   <- ?pos(explorer1, X, Y);
      .send(manager, tell, collision(1, X, Y));
      .print("Explorer1 waiting for manager...");
      .wait(5000);
      !doneOrNot.


+!doneOrNot: not done
   <- !doneOrNot.

+!doneOrNot: done
   <- .print("Collision resolved by manager.");
      !get_moves;
      !get_steps;
      !move.