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

echo "Select the type of job you want to submit:"
echo "1) email.send"
echo "2) report.generate"
echo "3) notification.push"
echo "4) data.export"
echo "5) test"
read -p "Enter choice [1-5]: " JOB_CHOICE

case $JOB_CHOICE in
  1)
    echo "Please enter the real email address you want to send this to:"
    read -p "Email: " USER_EMAIL
    if [ -z "$USER_EMAIL" ]; then
      echo "Email cannot be empty."
      exit 1
    fi
    JOB_TYPE="email.send"
    JOB_PRIORITY=1
    JOB_PAYLOAD="{\"to\": \"$USER_EMAIL\", \"subject\": \"Job scheduler is working\", \"body\": \"Hello! The distributed job scheduler successfully processed this job from the queue.\"}"
    ;;
  2)
    JOB_TYPE="report.generate"
    JOB_PRIORITY=3
    JOB_PAYLOAD="{\"reportType\": \"monthly_sales\", \"format\": \"pdf\", \"dateRange\": \"2026-06-01_2026-06-30\"}"
    ;;
  3)
    JOB_TYPE="notification.push"
    JOB_PRIORITY=1
    JOB_PAYLOAD="{\"userId\": \"user_98765\", \"deviceType\": \"ios\", \"message\": \"Your report is ready to download.\"}"
    ;;
  4)
    JOB_TYPE="data.export"
    JOB_PRIORITY=4
    JOB_PAYLOAD="{\"targetBucket\": \"s3://jobqueue-exports/users\", \"format\": \"csv\", \"includeDeleted\": false}"
    ;;
  5)
    JOB_TYPE="test"
    JOB_PRIORITY=5
    JOB_PAYLOAD="{\"source\": \"cli-test\", \"timestamp\": \"$(date +%s)\"}"
    ;;
  *)
    echo "Invalid choice."
    exit 1
    ;;
esac

echo ""
echo "1️⃣  Submitting a job (type: $JOB_TYPE, priority: $JOB_PRIORITY)..."

# Submitting the job
RESPONSE=$(curl -s -X POST http://localhost:8081/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d "{
    \"type\": \"$JOB_TYPE\",
    \"payload\": $JOB_PAYLOAD,
    \"priority\": $JOB_PRIORITY,
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
