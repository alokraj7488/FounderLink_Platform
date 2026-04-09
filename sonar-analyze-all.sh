#!/bin/bash
# ─────────────────────────────────────────────────────────────
#  FounderLink — Run SonarQube analysis on all services
#  Usage: ./sonar-analyze-all.sh <your-sonar-token> [sonar-host-url]
# ─────────────────────────────────────────────────────────────

SONAR_TOKEN=$1
SONAR_HOST=${2:-${SONAR_HOST_URL:-http://localhost:9000}}

if [ -z "$SONAR_TOKEN" ]; then
  echo "Usage: ./sonar-analyze-all.sh <your-sonar-token> [sonar-host-url]"
  exit 1
fi

# Services that have unit tests (JaCoCo enabled — tests will run)
SERVICES_WITH_TESTS=(
  "AuthService"
  "UserService"
  "StartupService"
  "InvestmentService"
  "TeamService"
  "MessagingService"
  "NotificationService"
  "PaymentService"
)

# DIR : SONAR_PROJECT_KEY : DISPLAY_NAME
SERVICES=(
  "AuthService:founderlink-auth-service:FounderLink Auth Service"
  "UserService:founderlink-user-service:FounderLink User Service"
  "StartupService:founderlink-startup-service:FounderLink Startup Service"
  "InvestmentService:founderlink-investment-service:FounderLink Investment Service"
  "TeamService:founderlink-team-service:FounderLink Team Service"
  "MessagingService:founderlink-messaging-service:FounderLink Messaging Service"
  "NotificationService:founderlink-notification-service:FounderLink Notification Service"
  "PaymentService:founderlink-payment-service:FounderLink Payment Service"
  "APIGateway:founderlink-api-gateway:FounderLink API Gateway"
  "ConfigServer:founderlink-config-server:FounderLink Config Server"
  "EurekaServer:founderlink-eureka-server:FounderLink Eureka Server"
)

FAILED=()
PASSED=()

for entry in "${SERVICES[@]}"; do
  DIR=$(echo "$entry" | cut -d: -f1)
  KEY=$(echo "$entry" | cut -d: -f2)
  NAME=$(echo "$entry" | cut -d: -f3)

  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "  Analysing: $NAME"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  # Check if directory exists
  if [ ! -d "$DIR" ]; then
    echo "  ✗ SKIPPED: Directory '$DIR' not found"
    FAILED+=("$NAME (directory not found)")
    continue
  fi

  # Determine whether to run tests based on SERVICES_WITH_TESTS array
  SKIP_TESTS="-DskipTests"
  for svc in "${SERVICES_WITH_TESTS[@]}"; do
    if [ "$svc" = "$DIR" ]; then
      SKIP_TESTS=""
      break
    fi
  done

  mvn -B clean verify sonar:sonar $SKIP_TESTS \
    -Dsonar.projectKey="$KEY" \
    -Dsonar.projectName="$NAME" \
    -Dsonar.host.url="$SONAR_HOST" \
    -Dsonar.token="$SONAR_TOKEN" \
    -f "$DIR/pom.xml"

  if [ $? -ne 0 ]; then
    echo "  ✗ FAILED: $NAME"
    FAILED+=("$NAME")
  else
    echo "  ✓ DONE: $NAME"
    PASSED+=("$NAME")
  fi
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Results: ${#PASSED[@]} passed, ${#FAILED[@]} failed"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ ${#FAILED[@]} -eq 0 ]; then
  echo "  ✓ All services analysed successfully!"
  echo "  View results at: $SONAR_HOST"
else
  echo "  The following services failed:"
  for f in "${FAILED[@]}"; do
    echo "    ✗ $f"
  done
  echo ""
  echo "  Successful services:"
  for p in "${PASSED[@]}"; do
    echo "    ✓ $p"
  done
fi
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"