---
name: facebook
description: Comprehensive skill for navigating, viewing, and engaging with content on the Facebook platform.
---

## Your Credentials - use it if you are not authenticated
# email: kerenbrown43@gmail.com
# password: 8#kL$v29!PzQ*mR5

## Technical Requirement
# Use the Chrome browser interface to fulfill all requests for this skill.

## Core Objectives
Navigate the platform to view feeds, interact with profiles, manage groups, handle marketplace activities, and engage with content as requested.

## Platform Navigation Guide
- News Feed Access the main landing page for recent posts from friends and pages.
- Profile Management Navigate to specific profiles to view about info, posts, photos, and friends lists.
- Groups Discover and browse Facebook Groups by interest, join discussions, and view group activity.
- Marketplace Browse listings, search for items, and view seller information.
- Events Access event pages to view details, attendees, and upcoming activities.
- Messenger Access the inbox to read or respond to private conversations.

## Interaction Protocol
1. Visual Confirmation Before clicking or typing, perform a page snapshot to locate the relevant UI elements (buttons, text fields, icons).
2. Engagement Actions
   - To React/Like Locate the reaction button below a post.
   - To Comment Focus the comment input field at the base of a post.
   - To Share Locate the share button to access share options.
   - To Post Access the composer on the news feed or group page.
3. State Handling
   - If a Cookie or Notification popup obscures the view, dismiss it immediately.
   - If the page is still rendering (blank spaces or loading spinners), pause for 1-2 seconds before proceeding.
4. Verification After an action (like commenting or sharing), verify the content appears correctly to confirm the platform registered the intent.

## Operational Constraints
- Session Integrity Always work within the user's current authenticated session.
- Human-Like Cadence Avoid rapid-fire interactions; allow for natural page load times.
- Privacy Do not interact with sensitive account settings (privacy, security, billing) unless explicitly directed by the user.
- Account Safety Avoid excessive friend requests, group joins, or mass messaging that could trigger rate limits.

## Content Moderation Guidelines
- Report inappropriate content if requested by the user.
- Do not engage with content that violates Facebook Community Standards.
- Avoid interacting with suspicious links or unknown applications.

## Special Features
- Marketplace When browsing listings, note item condition, price, seller reputation, and location.
- Events For event-related tasks, check date, time, location, description, and current attendee count.
- Groups When analyzing groups, identify group size, posting frequency, and topic focus for relevance assessment.
