# Privacy Policy of Blueshell E-Sports Association Enschede

Last updated: February 23, 2026  
Location: Enschede, The Netherlands

## Abbreviations

- `GMM`: General Members Meeting
- `UT`: University of Twente

This document contains the privacy policy of ESA Blueshell E-Sports Association Enschede ("Blueshell", "we", "us", "
our"). It applies to anyone who creates or uses a website account (including non-members), and to broader association
operations (for example membership administration, activities, CRM, and communication tooling).  
By agreeing to this privacy policy when creating/using an account, you consent to photography and image capture during
association events.  
For questions and/or complaints, you can always contact the board of the Association via `board@blueshell.utwente.nl`.

## Data Register

### Website account with additional membership/profile information

**Contract Type**: Website account contract, and membership contract where applicable
**Processing**: Account administration, member administration (where applicable), contribution collection, information
provision, invitation to meetings (including `GMM`), communication, event participation, and statistics  
**Processing by**: Board of the Association, authorized committees (limited to event-related data), and authorized
administrators  
**Retention Period**: See section "Retention Period"  
**Security**: Role-based access control, secure association storage, soft-delete model, secure transport (HTTPS), hashed
credentials, CSRF protection, and audited changes

Personal data currently processed for account management and, where applicable, membership management includes:

1. Membership number / internal user ID  
   Required for complete administration and for statistics (for example member-flow analysis per academic period).
2. Name fields (`initials`, `first name`, optional prefix, `last name`, `full name`)  
   Used to identify account holders/members in administration and communication, and for payment-related
   administration.
3. Username  
   Used for account login and identification in internal systems.
4. Email address  
   Used as the primary communication channel for account communication, activation, password recovery, newsletters,
   official announcements, and convening/informing members regarding the General Members Meeting (`GMM`).
5. Password (hashed)  
   Used for account authentication. Passwords are not stored in plaintext.
6. Phone number  
   Used as a backup contact method for urgent matters when other communication channels are insufficient.
7. Discord handle  
   Used to identify members/guests in community channels and support event/community coordination.
8. Newsletter preference  
   Used to determine which mailing/contact communications are sent to an account holder.
9. Address details (`country`, `city`, `street`, `house number`, `zip code`)  
   Used for account/membership administration and paper/postal communication where required.
10. Member profile details (`date of birth`, `student number`, `gender`, `nationality`, `photo consent`, `BHV`,
    `EHBO`)  
    Used for member administration, statistics/completeness, safety planning, and association obligations:
    - `student number`: UT-related matters (for example subsidy administration or institutional linkage where needed).
    - `date of birth`: administrative completeness and statistics.
    - `EHBO`/`BHV`: planning safe staffing for activities (inside and outside UT contexts).
    - `photo consent`: acceptance of this privacy policy constitutes consent to photography/image capture at events;
      this is not handled as a separate opt-in.
11. Membership details (`member type`, `start date`, `end date`, `incasso`)  
    Used for membership status and contribution/payment administration:
    - `member type`: required because statutory/administrative differences can apply across member categories.
    - `start date`/`end date`: required for membership lifecycle administration and statistics.
    - `incasso`: tracks payment collection method.
12. Role and permission data  
    Used for authorization in the application (for example board, committee, member roles) and to control which
    internal working areas/data are accessible.
13. Group memberships (`Board`, `Committees`, `Teams`)  
    Used to manage access to internal working environments and for association statistics.
14. Bank account details (`IBAN`/`BIC`)  
    Used to identify payments and execute direct-debit/payment administration.
15. Direct-debit consent  
    Used to record whether automatic direct debit collection is authorized.
16. Contribution and reminder records  
    Used to track payment compliance, outstanding amounts, and reminder workflows.
17. Mailing list registration state  
    Used to track which communication channels (for example newsletter/active-members list) apply to an account
    holder/member.
18. Contact synchronization reference (`contact_id`)  
    Used to synchronize contacts with external contact management processors.
19. Audit metadata (`created/updated timestamps`, actor tracking in management flows)  
    Used for operational integrity, security, and accountability.

### Activity Form (Event Sign-up)

**Contract Type**: Event registration form (member or guest)  
**Processing**: Event participation management, attendance administration, event communication, and optional questionnaire
handling  
**Processing by**: Board of the Association, event-organizing committees  
**Retention Period**: See section "Retention Period"  
**Security**: Authenticated access controls, guest access-token flow with hashed token storage, and role-based access

Personal data currently processed for event sign-up may include:

1. Name  
   Required to identify the participant.
2. Email  
   Required as the primary communication channel between organizers and participants.
3. Discord username  
   Used when needed for game/event coordination (for example tournament/custom-game invites).
4. Phone number  
   Used as a backup contact method for urgent operational communication.
5. Event-specific questionnaire answers (including free-text answers)  
   Used to organize activities (for example participation preferences, planning choices, and resource/logistics input).
6. For guest sign-ups: a guest access token (stored as hash in the database; raw token is used for guest edit/delete
   flows)  
   Required to let guests securely edit or delete their own sign-up without creating a full account.
7. In-game name (optional)  
   Used when relevant for game-specific organizing (for example tournament/custom-game invites).
8. Activity preferences (optional)  
   Used when organizers include preference questions in a sign-up flow.
9. Available resources (optional)  
   Used when organizers ask participants what resources they can provide (for example power strips/equipment).

### CRM / Contact Synchronization

**Contract Type**: Contact synchronization for communication and contribution-related list management  
**Processing**: Synchronizing contact details with external contact processor and contribution-period list operations  
**Processing by**: Board of the Association and authorized technical operators  
**Retention Period**: See section "Retention Period"  
**Security**: Access-limited job processing and authenticated processor APIs

Data used for contact synchronization includes:

1. Email  
   Used for communication and mailing-list synchronization.
2. First and last name  
   Used to identify the contact and keep correspondence records clear.
3. Phone number (if available)  
   Used as an additional contact channel when needed.
4. Organization/company  
   Used to identify the contact context and maintain relevant communication history.
5. Notes  
   Used to store relevant contextual information regarding the contact/contact person.
6. Newsletter preference  
   Used to include/exclude contacts from communication campaigns in line with their preference.
7. Member-status indicators  
   Used to synchronize contribution-period and membership-related contact lists correctly.

### Discord and community channels

**Contract Type**: Community participation and optional external community integrations  
**Processing**: Community coordination and visibility of public community information  
**Processing by**: Members/guests themselves, Discord platform, and board/committee channels where relevant  
**Retention Period**: Controlled by platform/account lifecycle and internal records where applicable  
**Security**: Platform-level controls and association access controls

Notes:

- Blueshell stores the Discord handle provided by a user/guest in account or event-sign-up flows.
- The website may request Discord widget data from Discord's public endpoint to display online presence/channel
  information.
- Optional Discord game preferences may be used for statistics and member-to-member discoverability.
- Discord group/role membership (for example member/committee/team roles) may be visible within Discord and used for
  identification and internal coordination.

### Security, authentication, and recovery processing

**Contract Type**: Account security and fraud/abuse prevention  
**Processing**: Authentication, authorization, session handling, CSRF protection, account recovery, and abuse-rate
limiting  
**Processing by**: Blueshell platform and authorized operators  
**Retention Period**: See section "Retention Period"  
**Security**: Secure cookies, hashed credentials/tokens, role-based permissions, and protected endpoints

Data processed includes:

1. Authentication token data (JWT/session cookie handling)
2. CSRF token data
3. Recovery token selector/verifier-hash, expiry, and consumed state
4. Security event metadata (for example actor ID, role/type in operational job tracking)
5. Limited IP-based metadata for public auth endpoint rate limiting

## Retention Period

Retention periods vary per processing purpose and technical subsystem.

### Membership and account data

- Account, profile, address, membership, and related administration records are retained until a deletion request is
  submitted to the board through official channels.
- When you request deletion of your account, the account enters a 90-day recovery period. During this period, your
  account can be restored on request.
- After 90 days, your account data is irreversibly removed or anonymized, and restoration is no longer possible.
- Where legal or financial obligations apply, specific records may be retained in a minimized or legally required
  form.

### Activity form / event sign-ups

- Event sign-ups, guest records, and linked questionnaire answers are retained.
- After a deletion request is processed, related activity-form and event-sign-up records are de-identified so they no
  longer remain personally identifiable.

### CRM / contact synchronization

- When a deletion request is processed, corresponding contact records are deleted from CRM/contact synchronization
  processors.

### Security and recovery data

- Recovery tokens have built-in expiration windows.
- Expired or consumed token rows may remain until cleanup.
- Rate-limit buckets are process-local and short-lived.

### Operational job records

- Job execution records (status, payload metadata, error details, and actor metadata) are retained for operational
  monitoring and troubleshooting.
- Operational job records are automatically purged after 1 year.

## Provision of Data / Processor Agreement

### Internal

Within the Association, data is shared on a need-to-know basis with:

- Board members (broad administrative access where required by role).
- Committees (limited access, mainly event-related participant data for events they organize).
- Authorized administrators/technical operators.

Data sharing may vary by body, timing, and activity. Data is provided for purposes such as participant communication,
organizing activities, and attendance administration. Full member administration remains board-controlled.

### External

Blueshell currently uses external processors/services for specific purposes:

1. ING  
   Executes direct-debit/payment processing for the association where applicable.
2. NBSE (Nederlandse Bond voor Studenten E-sportsverenigingen)  
   Receives agreed membership-related data (for example first name, surname, email, city, address) for
   association-level membership administration and voting-right calculations.
3. SMTP email provider (hornet.snt.utwente.nl)
   Used to deliver transactional emails (activation, recovery, reminders, event communication).
4. Brevo contact processor
   Used for contact synchronization and contribution-period mailing list operations.
5. Google Calendar  
   Used to synchronize approved event data (title, description, location, start/end times).
6. Third-party services accessed by users in frontend features  
   For example Google Maps embeds, Discord widget endpoints, Google Calendar links, and social-media links.

### Outside the EU/EEA

Depending on processor infrastructure and user interaction with third-party services, personal data may be processed
outside the EU/EEA.  
Where required, Blueshell aims to rely on lawful transfer mechanisms under GDPR.

Association-wide tooling and communication processors that may involve non-EU/EEA processing include:

1. Google Inc. / Google services  
   Association administration and collaboration tooling can involve Google services (for example shared drives and
   calendar tooling), resulting in processor data exchange.
2. Mailchimp  
   Newsletter/info mail distribution can involve sharing email addresses and, where relevant, name data for mailing
   operations.
3. Brevo  
   Contact synchronization and mailing operations can involve processing through Brevo infrastructure, which may
   involve non-EU/EEA processing depending on service configuration and routing.

Where applicable, Blueshell maintains processor/data-processing agreements with processors to define privacy rights and
obligations.

## Rights of Individuals

Under GDPR, you have the following rights (subject to legal conditions and exceptions):

### Right of access

You have the right to access your data and processing purposes (Article 15 GDPR).  
Requests can be submitted to the board via official channels.  
Blueshell aims to provide access quickly and, where feasible, within one working day for straightforward requests.

### Right to rectification

You have the right to rectify incorrect personal data (Article 16 GDPR).

### Right to erasure

You have the right to request deletion of your data (Article 17 GDPR).  
If financial/legal obligations still require specific data, those records may be retained in minimized/anonymized form
as legally required.

### Right to restrict processing

You can request restriction of processing where legally applicable.

### Notification obligation

If rectification, deletion, or restriction is applied, Blueshell will inform relevant recipients/processors where
required.

### Right to data portability

You can request an export of your known personal data in a commonly used format (for example an Excel-compatible
export where applicable).

### Right to object

You can object to specific processing. If the objection concerns mandatory processing required for membership or
activity participation, this can affect the ability to maintain that membership/participation.

### Right to withdraw consent

Where processing is based on consent, you can withdraw consent at any time.

To exercise rights, contact the board via `board@blueshell.utwente.nl`.

## Provision of Personal Data to Third Parties

Without your consent, Blueshell only provides personal data to third parties when this is necessary for agreement
performance, legitimate association operations, security, or legal obligations.

## Access, Correction, and Deletion of Personal Data

You may request access, correction, or deletion of your personal data.  
Deletion requests should be submitted to the board through official channels (for example via
`board@blueshell.utwente.nl`).  
For requests, Blueshell may ask you to identify yourself to protect your data.
Where applicable, Blueshell will communicate completed deletion to processors/organizations that received the relevant
data.
For deletion requests, data needed for legal/financial obligations may be retained or minimized as legally required.

## Security of Personal Data

Blueshell takes appropriate technical and organizational measures, including:

- Role-based authorization
- Secure transport (HTTPS)
- Secure authentication cookie handling
- CSRF defenses
- Password hashing
- Token hashing where applicable
- Audited data changes and operational logging
- Access control based on necessity ("need to know")
- Periodic review of security controls

## Links to Other Websites

The website may contain links or embeds from third-party websites/services.  
This privacy policy applies only to Blueshell-controlled processing, not to independent third-party policies.

## Amendment of the Privacy Policy

Blueshell may update this privacy policy to keep it accurate and current.  
The latest version will be published through official Blueshell channels.  
In case of significant changes, Blueshell will make reasonable efforts to inform members via official communication
channels.

## Data Breaches

In case of a personal-data breach, Blueshell will act in accordance with GDPR obligations, including:

- Assessment and containment
- Notification to supervisory authority where required (without undue delay, and where feasible within 72 hours)
- Notification to affected individuals where legally required
- Investigation of likely cause and preventive follow-up actions

## Contact Information

Responsible: Board of Blueshell E-Sports Association Enschede  
Email: `board@blueshell.utwente.nl`

## Complaint about the Processing of Your Personal Data

If you have a complaint, please contact us first so we can try to resolve it.  
You also have the right to lodge a complaint with the Dutch Data Protection Authority (`Autoriteit Persoonsgegevens`).
