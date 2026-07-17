import os
import re

md_path = "/Users/rishi/Developer/js/dev/Java/02_Project/03_Perosnal/01_Distributed_Job_Scheduler/01_V1/job-scheduler/Docs/ExecutionPlan.md"
base_dir = "/Users/rishi/Developer/js/dev/Java/02_Project/03_Perosnal/01_Distributed_Job_Scheduler/01_V1/job-scheduler/jobqueue-system"

with open(md_path, "r") as f:
    lines = f.readlines()

current_file = None
current_content = []
in_code_block = False

for line in lines:
    file_match = re.search(r'\*\*(?:File|Test): `([^`]+\.(?:java|lua))`\*\*', line)
    if file_match:
        current_file = file_match.group(1)
        current_content = []
        in_code_block = False
        continue
    
    if current_file and line.startswith("```"):
        if not in_code_block:
            in_code_block = True
            current_content = []
        else:
            in_code_block = False
            full_path = os.path.join(base_dir, current_file)
            os.makedirs(os.path.dirname(full_path), exist_ok=True)
            with open(full_path, "w") as out:
                out.write("".join(current_content))
            print(f"Created: {current_file}")
            current_file = None
            current_content = []
        continue
        
    if in_code_block:
        current_content.append(line)
