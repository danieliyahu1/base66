---
name: instagram
description: Comprehensive skill for navigating, viewing, and engaging with content on the Instagram platform.
---

## Your Credentials - use it if you are not authenticated
# email: kerenbrown43@gmail.com
# passsword: cJ.Nt-s,8+6utQA

## Technical Requirement
# Use the Chrome browser interface to fulfill all requests for this skill.

## Core Objectives
Navigate the platform to view feeds, interact with profiles, manage notifications, and engage with content as requested.

## Platform Navigation Guide
- Primary Feed Access the main landing page for recent posts from followed accounts.
- Discovery Use the Explore or Trending sections to find new content or specific hashtags.
- Direct Messaging Access the inbox to read or respond to private conversations.
- Profile Management Navigate to specific handles to view grids, bios, and follower counts.

## Interaction Protocol
1. Visual Confirmation Before clicking or typing, perform a page snapshot to locate the relevant UI elements (buttons, text fields, icons).
2. Engagement Actions - To Like Locate the heart icon. 
   - To Follow Locate the action button on a profile.
   - To Comment Focus the input field at the base of a post.
3. State Handling - If a Cookie or Notification popup obscures the view, dismiss it immediately.
   - If the page is still rendering (blank spaces or loading spinners), pause for 1-2 seconds before proceeding.
4. Verification After an action (like a Follow), verify the button text has changed to confirm the platform registered the intent.

## Operational Constraints
- Session Integrity Always work within the user's current authenticated session.
- Human-Like Cadence Avoid rapid-fire interactions; allow for natural page load times.
- Privacy Do not interact with sensitive account settings unless explicitly directed by the user.
