#!/bin/bash
set -eo pipefail

# Submit Sample Job Script
# This script interacts with the Job Scheduler to submit a job and track its progress

echo "==============================================="
echo "🚀 Distributed Job Scheduler - Sample Job"
echo "==============================================="
echo ""

# Ensure the submission service is up
if ! curl -s http://localhost:8081/actuator/health | grep -q '"status":"UP"'; then
  echo "❌ Submission Service is not running. Please run './start-all.sh' first."
  exit 1
fi

echo "Please enter the real email address you want to send this to:"
read -p "Email: " USER_EMAIL

if [ -z "$USER_EMAIL" ]; then
  echo "Email cannot be empty."
  exit 1
fi

echo "1️⃣  Submitting a job (type: email.send, priority: 1) to $USER_EMAIL..."

# Submitting the job
RESPONSE=$(curl -s -X POST http://localhost:8081/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d "{
    \"type\": \"email.send\",
    \"payload\": {
      \"to\": \"$USER_EMAIL\",
      \"subject\": \"Job scheduler is working\",
      \"body\": \"Hello! The distributed job scheduler successfully processed this job from the queue.\"
    },
    \"priority\": 1,
    \"maxRetries\": 3,
    \"timeoutMs\": 5000
  }")

# Extract Job ID
JOB_ID=$(echo "$RESPONSE" | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$JOB_ID" ]; then
    echo "❌ Failed to submit job. Response:"
    echo "$RESPONSE"
    exit 1
fi

echo "✅ Job successfully submitted!"
echo "   Job ID: $JOB_ID"
echo ""

echo "2️⃣  Polling Job Status every 1 second..."
echo "-----------------------------------------------"

# Poll the job status up to 5 times
for i in {1..5}; do
  STATUS_RESP=$(curl -s "http://localhost:8081/api/v1/jobs/$JOB_ID")
  
  # Format output beautifully using jq if available, else fallback
  STATUS=$(echo "$STATUS_RESP" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
  
  if [ -z "$STATUS" ]; then
      echo "   ⚠️ Attempt $i: Could not parse status. Raw response: $STATUS_RESP"
  else
      echo "   Attempt $i: Status is [ $STATUS ]"
      if [[ "$STATUS" == "COMPLETED" || "$STATUS" == "FAILED" || "$STATUS" == "DEAD" ]]; then
          echo ""
          echo "🎉 Job reached terminal state: $STATUS"
          echo "Final Job Details:"
          if command -v jq &> /dev/null; then
              echo "$STATUS_RESP" | jq .
          else
              echo "$STATUS_RESP"
          fi
          exit 0
      fi
  fi
  sleep 1
done

echo ""
echo "⏳ Job is still in [ $STATUS ] after 5 seconds."
echo "You can manually check it later using:"
echo "curl -s http://localhost:8081/api/v1/jobs/$JOB_ID"
