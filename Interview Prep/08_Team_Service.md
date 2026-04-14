# Team Service — Interview Preparation Guide

---

## What does Team Service do?

The Team Service manages **startup team formation**. A founder can invite users (who have roles like co-founder, developer, etc.) to join their startup's team. The invited user can accept or reject the invitation.

**Real-world scenario:**
- Alice (Founder) creates startup "TechCorp"
- Alice wants Bob (registered as Co-Founder on FounderLink) to join her team
- Alice invites Bob via `POST /teams/invite`
- Bob receives a notification (via RabbitMQ → NotificationService)
- Bob accepts via `POST /teams/join/{invitationId}`
- Bob is now a team member of TechCorp

---

## Key Features

1. **Invite Co-Founder** — Founder invites a user to the startup team
2. **Accept Invitation** — Invited user accepts and becomes a team member
3. **Reject Invitation** — Invited user declines
4. **Update Team Role** — Founder changes a member's role
5. **View Team** — Anyone can view a startup's team members
6. **My Invitations** — User views their pending invitations
7. **Feign Clients** — Calls StartupService and UserService for validation
8. **Circuit Breaker** — Graceful fallback if either service is down
9. **Redis Caching** — Team and invitation data cached

---

## Architecture

```
[Founder: POST /teams/invite {startupId, invitedUserId, role}]
    ↓
[TeamController]
    ↓
[TeamService]
    ├── Feign → StartupService: GET /startups/{id}  (get startup + founderId)
    ├── Validate: caller must be the startup's founder
    ├── Save TeamInvitation to DB (status=PENDING)
    └── Publish team.invite.sent event → RabbitMQ
         ↓
[NotificationService receives event → notifies invited user]

[Invited User: POST /teams/join/{invitationId}]
    ↓
[TeamService]
    ├── Load invitation from DB
    ├── Validate: caller is the invited user
    ├── Update invitation status = ACCEPTED
    └── Save TeamMember to DB
```

---

## File 1: TeamController.java

```java
@RestController
@RequestMapping("/teams")
@RequiredArgsConstructor
public class TeamController {

    @PostMapping("/invite")
    @PreAuthorize("hasAuthority('ROLE_FOUNDER') or hasAuthority('ROLE_COFOUNDER')")
    public ResponseEntity<InvitationResponse> inviteCoFounder(
            @Valid @RequestBody InvitationRequest request,
            Authentication authentication) {
        Long founderId = Long.parseLong(authentication.getName());
        InvitationResponse response = teamCommandService.inviteCoFounder(request, founderId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/join/{invitationId}")
    public ResponseEntity<TeamMemberResponse> acceptInvitation(
            @PathVariable Long invitationId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return new ResponseEntity<>(teamCommandService.acceptInvitation(invitationId, userId), HttpStatus.OK);
    }

    @GetMapping("/startup/{startupId}")
    public ResponseEntity<List<TeamMemberResponse>> getTeamByStartup(
            @PathVariable Long startupId) {
        return ResponseEntity.ok(teamQueryService.getTeamByStartup(startupId));
    }
```

**`/join/{invitationId}` has no `@PreAuthorize`** — The security check is done inside the service layer (`if (!invitation.getInvitedUserId().equals(userId))`). The endpoint is accessible to any authenticated user, but the service validates that the caller is the invited user.

**`getTeamByStartup` is public (no `@PreAuthorize`)** — Team composition is publicly visible so investors can see who is running the startup.

---

## File 2: TeamService.java (Core Logic)

### inviteCoFounder:

```java
@Override
@Transactional
@Caching(evict = {
    @CacheEvict(value = "teamInvitations", key = "#request.invitedUserId")
})
public InvitationResponse inviteCoFounder(InvitationRequest request, Long founderId) {

    // Step 1: Validate startup exists and get founderId
    StartupDTO startup = fetchStartup(request.getStartupId());
    if (startup == null) {
        throw new ResourceNotFoundException("Startup not found");
    }

    // Step 2: Caller must be the startup's founder
    if (startup.getFounderId().longValue() != founderId.longValue()) {
        throw new UnauthorizedException("Only the startup founder can invite team members");
    }

    // Step 3: Self-invitation guard
    if (request.getInvitedUserId().equals(founderId)) {
        throw new BadRequestException("You are already the founder of this startup");
    }

    // Step 4: Not already a member
    if (memberRepository.existsByStartupIdAndUserId(request.getStartupId(), request.getInvitedUserId())) {
        throw new DuplicateResourceException("This user is already a member of the team");
    }

    // Step 5: No duplicate pending invitation
    invitationRepository.findByStartupIdAndInvitedUserIdAndStatus(
            request.getStartupId(), request.getInvitedUserId(), InvitationStatus.PENDING
    ).ifPresent(existing -> {
        throw new DuplicateResourceException("A pending invitation already exists");
    });

    // Step 6: Auto-add founder as FOUNDER member (if not already added)
    if (!memberRepository.existsByStartupIdAndUserId(request.getStartupId(), founderId)) {
        memberRepository.save(TeamMember.builder()
                .startupId(request.getStartupId())
                .userId(founderId)
                .role(TeamRole.FOUNDER)
                .build());
    }

    // Step 7: Save invitation
    TeamInvitation invitation = TeamInvitation.builder()
            .startupId(request.getStartupId())
            .invitedUserId(request.getInvitedUserId())
            .role(request.getRole())
            .status(InvitationStatus.PENDING)
            .build();
    invitation = invitationRepository.save(invitation);

    // Step 8: Publish event
    eventPublisher.publishTeamInviteSent(TeamInviteSentEvent.builder()
            .invitationId(invitation.getId())
            .startupId(invitation.getStartupId())
            .invitedUserId(invitation.getInvitedUserId())
            .role(invitation.getRole().name())
            .build());

    return teamMapper.toInvitationResponse(invitation);
}
```

**Key design decisions:**

**Step 2 — Authorization at service level, not just controller level:**
- `@PreAuthorize` at the controller allows ROLE_FOUNDER or ROLE_COFOUNDER to call the endpoint.
- But we also verify: is this caller the founder OF THIS SPECIFIC STARTUP?
- A founder could try to send invitations for another founder's startup — the service-level check prevents this.

**Step 4 — Duplicate member check:**
- `memberRepository.existsByStartupIdAndUserId()` — a custom Spring Data JPA derived query method.
- Spring generates: `SELECT COUNT(*) FROM team_members WHERE startup_id = ? AND user_id = ?`

**Step 5 — `ifPresent()` with exception:**
- `Optional.ifPresent()` runs the lambda only if the Optional has a value.
- This idiom: "if a pending invitation already exists for this user+startup pair, throw."

**Step 6 — Auto-add founder:**
- When a founder invites their first team member, they should also appear in the team list.
- This auto-adds them as `TeamRole.FOUNDER` if they're not already there.

### acceptInvitation:

```java
@Override
@Transactional
@Caching(evict = {
    @CacheEvict(value = "teamInvitations", key = "#userId"),
    @CacheEvict(value = "teamMembers", allEntries = true)
})
public TeamMemberResponse acceptInvitation(Long invitationId, Long userId) {
    TeamInvitation invitation = invitationRepository.findById(invitationId)
            .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

    if (!invitation.getInvitedUserId().equals(userId)) {
        throw new UnauthorizedException("This invitation is not for the current user");
    }

    if (invitation.getStatus() != InvitationStatus.PENDING) {
        throw new BadRequestException("Invitation is not in PENDING status");
    }

    invitation.setStatus(InvitationStatus.ACCEPTED);
    invitationRepository.save(invitation);

    TeamMember member = TeamMember.builder()
            .startupId(invitation.getStartupId())
            .userId(userId)
            .role(invitation.getRole())
            .build();
    member = memberRepository.save(member);

    return teamMapper.toMemberResponse(member);
}
```

Two atomic operations happen: 
1. Invitation status updated to ACCEPTED.
2. TeamMember record created.

Both are in `@Transactional` — if either fails, both are rolled back.

### getTeamByStartup (with auto-founder-sync):

```java
@Override
@Transactional
@Cacheable(value = "teamMembers", key = "#startupId")
public List<TeamMemberResponse> getTeamByStartup(Long startupId) {
    // Ensure founder is always in the team list
    if (!memberRepository.existsByStartupIdAndRole(startupId, TeamRole.FOUNDER)) {
        StartupDTO startup = fetchStartup(startupId);
        if (startup != null && startup.getFounderId() != null) {
            memberRepository.save(TeamMember.builder()
                    .startupId(startupId)
                    .userId(startup.getFounderId())
                    .role(TeamRole.FOUNDER)
                    .build());
        }
    }
    return memberRepository.findByStartupId(startupId).stream()
            .map(teamMapper::toMemberResponse)
            .collect(Collectors.toList());
}
```

This auto-sync ensures that even for startups created before TeamService had the auto-add-founder logic, the founder always appears in the team list.

---

## File 3: TeamInvitation.java (Entity)

```java
@Entity
@Table(name = "team_invitations")
public class TeamInvitation {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeamRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = InvitationStatus.PENDING;
        }
    }
}
```

**InvitationStatus enum:**
```java
public enum InvitationStatus {
    PENDING,   // Awaiting response
    ACCEPTED,  // Accepted and team member created
    REJECTED   // Declined by invited user
}
```

**TeamRole enum:**
```java
public enum TeamRole {
    FOUNDER,
    CO_FOUNDER,
    DEVELOPER,
    DESIGNER,
    MARKETING,
    ADVISOR
}
```

---

## File 4: EventPublisher.java

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key.team-invite-sent}")
    private String teamInviteSentRoutingKey;

    public void publishTeamInviteSent(TeamInviteSentEvent event) {
        log.info("Publishing TEAM_INVITE_SENT event for invitation: {}", event.getInvitationId());
        rabbitTemplate.convertAndSend(exchange, teamInviteSentRoutingKey, event);
    }
}
```

**`@Value("${rabbitmq.routing-key.team-invite-sent}")`**
- Reads the routing key from the config file (which reads from Config Server).
- Value: `"team.invite.sent"`
- NotificationService has a queue bound to this routing key in `founderlink.exchange`.

This separation (EventPublisher as a dedicated class) follows Single Responsibility Principle — keeps RabbitMQ publishing logic out of the business logic service.

---

## File 5: Feign Clients (StartupClient and UserClient)

```java
@FeignClient(name = "startup-service", ...)
public interface StartupClient {
    @GetMapping("/startups/{id}")
    StartupDTO getStartupById(@PathVariable("id") Long id);
}

@FeignClient(name = "user-service", ...)
public interface UserClient {
    @GetMapping("/users/{id}")
    UserDTO getUserById(@PathVariable("id") Long id);
}
```

TeamService calls two external services:
- **StartupClient** — to validate the startup exists and get founderId for authorization.
- **UserClient** — to validate the invited user exists (if implemented).

Both have `FallbackFactory` implementations with Circuit Breaker protection.

---

## Interview Q&A

**Q: What is the TeamInvitation flow end-to-end?**

The founder creates an invitation via `POST /teams/invite` with `{startupId, invitedUserId, role}`. TeamService calls StartupService to verify the startup exists and the caller is its founder. After all validations pass, a `TeamInvitation` record is saved with `status=PENDING`. An event `team.invite.sent` is published to RabbitMQ. NotificationService receives this event and creates a notification for the invited user. The invited user calls `POST /teams/join/{invitationId}` to accept. TeamService validates the invitation belongs to them and is PENDING, updates it to ACCEPTED, and creates a `TeamMember` record. Both the invitation update and member creation happen in one `@Transactional` method.

**Q: How does TeamService prevent a founder from inviting themselves?**

```java
if (request.getInvitedUserId().equals(founderId)) {
    throw new BadRequestException("You are already the founder of this startup");
}
```
The `founderId` comes from `authentication.getName()` (the JWT-validated userId), and `invitedUserId` comes from the request body. If they're equal, we throw a `BadRequestException` which GlobalExceptionHandler converts to HTTP 400.

**Q: What is the difference between `@Cacheable` and `@CacheEvict`?**

`@Cacheable` stores the method's return value in the cache and serves subsequent calls from the cache. `@CacheEvict` removes entries from the cache, typically when data is modified. For example, when a new team member is added (`acceptInvitation`), we `@CacheEvict` the `teamMembers` cache for that startup because the cached list is now stale. The next call to `getTeamByStartup()` will fetch fresh data from the database and re-cache it.

**Q: Why does TeamService need to call StartupService and not just store the founderId locally?**

TeamService is a separate microservice with its own database. It doesn't have a copy of the startup data. To validate that the person inviting is actually the founder of that specific startup, TeamService must ask StartupService. This inter-service call is made via the `StartupClient` Feign client. The returned `StartupDTO` contains the `founderId` which is compared to the `founderId` extracted from the JWT. This is the correct approach — each service owns its data, and services communicate via APIs.
