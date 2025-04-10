# This was made to count the rewards and scores from the run_results.txt file

import re
from collections import Counter

# File path to your game log
file_path = 'pathfinder/run_results.txt'

# Initialize variables
final_scores = []
reward_counter = Counter()

# Read and process the log file
with open(file_path, 'r') as file:
    for line in file:
        # Extract rewards
        reward_match = re.search(r'Collected (\w+) reward', line)
        if reward_match:
            reward_color = reward_match.group(1)
            reward_counter[reward_color] += 1
        
        # Extract final scores
        score_match = re.search(r'Final score: ([\d.]+)', line)
        if score_match:
            final_score = float(score_match.group(1))
            final_scores.append(final_score)

# Display results
# Format final scores to 2 decimal places
print("Final Scores from all runs:")
for score in final_scores:
    print(f"{score:.2f}")

print("\nNumber of rewards collected by color:")
for color, count in reward_counter.items():
    print(f"{color}: {count}")