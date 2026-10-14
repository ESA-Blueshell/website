import type {HelpSection} from "./help"

/**
 * Everything the Help page can say, for every role. `helpFor` picks what a reader may act on.
 *
 * A topic names buttons, tabs and pages by the words the site shows, so a label changed on a page
 * has to change here too.
 */
export const HELP: HelpSection[] = [
  {
    id: "account",
    title: "Your account",
    topics: [
      {
        id: "account-details",
        title: "Change your details",
        audience: "everyone",
        body: [
          "On the Account tab, you can change your Discord account, phone number, newsletter choice and photo consent. You can also fill in your date of birth, nationality, gender and student number. And you can say whether you hold EHBO (first aid) or BHV (emergency response) certificates.",
          "Press Submit to save. The site may ask for your password first. This confirms it is you.",
        ],
        links: [{label: "Account", to: "/account"}],
      },
      {
        id: "account-locked-fields",
        title: "Change your name or username",
        audience: "everyone",
        body: [
          "You cannot change your initials, first name, surname or username yourself. Ask the board. You can email board@blueshell.utwente.nl or post in #board-questions on our Discord.",
          "Your email address is different. You change it yourself under Security.",
        ],
        links: [{label: "Security", to: "/account/security"}],
      },
      {
        id: "account-address",
        title: "Change your address",
        audience: "everyone",
        body: [
          "The Address tab holds one address. It has your street, house number, zipcode, city and country. The association may use it for post and billing.",
          "Press Save address to save it.",
          "If the page says your address cannot be shown, fill it in again and save. Saving writes it again.",
        ],
        links: [{label: "Address", to: "/account/addresses"}],
      },
      {
        id: "account-delete",
        title: "Delete your account",
        audience: "everyone",
        body: [
          "You cannot delete your account yourself. Ask the board. The board can bring back a deleted account for 90 days.",
        ],
      },
    ],
  },
  {
    id: "security",
    title: "Signing in and security",
    topics: [
      {
        id: "security-password",
        title: "Change your password",
        audience: "everyone",
        body: [
          "Go to Security, then Password. Fill in your current password and your new password.",
          "Press Change password. You are signed out everywhere else.",
        ],
        links: [{label: "Password", to: "/account/security/password"}],
      },
      {
        id: "security-email",
        title: "Change your email address",
        audience: "everyone",
        body: [
          "Go to Security, then Email address. Fill in the new address and press Send confirmation link.",
          "Your account keeps the old address until you open the link in that email. The link works for 24 hours.",
          "Use Send the link again to get a new link.",
          "Your old address gets an email saying the address is changing.",
        ],
        links: [{label: "Email address", to: "/account/security/email"}],
      },
      {
        id: "security-two-factor",
        title: "Turn on two-factor authentication",
        audience: "everyone",
        body: [
          "When two-factor is on, signing in asks for your password. It also asks for a code from an authenticator app on your phone.",
        ],
        steps: [
          "Go to Security, then Two-factor authentication.",
          "Type your password again.",
          "Scan the QR code with your authenticator app, then press I have scanned it.",
          "Type the code the app shows, then press Check the code.",
          "Save the 10 backup codes somewhere safe. Each code works once, for when you do not have your phone.",
        ],
        links: [{label: "Two-factor authentication", to: "/account/security/two-factor"}],
      },
      {
        id: "security-backup-codes",
        title: "Get new backup codes or move to a new phone",
        audience: "everyone",
        body: [
          "On the Two-factor authentication page, press Create new codes. You get 10 new backup codes. The old codes stop working.",
          "The site warns you when fewer than 4 are left.",
          "Press Replace authenticator app to move two-factor to a new phone.",
        ],
        links: [{label: "Two-factor authentication", to: "/account/security/two-factor"}],
      },
      {
        id: "security-turn-off",
        title: "Turn off two-factor authentication",
        audience: "everyone",
        body: [
          "After you turn it off, only your password signs you in. The site forgets your trusted browsers.",
          "You cannot turn it off while you have a board, treasurer or admin role. The page then has no Turn off button. You can still replace the app.",
        ],
        steps: [
          "Go to Security, then Two-factor authentication.",
          "Press Turn off.",
          "Confirm it is you when the site asks.",
        ],
        links: [{label: "Two-factor authentication", to: "/account/security/two-factor"}],
      },
      {
        id: "security-trust-browser",
        title: "Skip the code on your own computer",
        audience: "everyone",
        body: [
          "When you sign in with a code, tick Trust this browser for 30 days. That browser does not ask for a code again for 30 days.",
          "Under Where you are signed in, press Forget to remove a trusted browser.",
        ],
      },
      {
        id: "security-sign-ins",
        title: "See where you are signed in",
        audience: "everyone",
        body: [
          "Go to Security, then Where you are signed in. This list shows every browser signed in to your account.",
          "Press Sign out to end one of them.",
          "Press Sign out everywhere else to keep only the browser you are using. Press Sign out everywhere to end them all.",
        ],
        links: [{label: "Where you are signed in", to: "/account/security/sign-ins"}],
      },
      {
        id: "security-log",
        title: "See your security log",
        audience: "everyone",
        body: [
          "The security log shows what happened to your account in the last twelve months. That includes sign-ins, password changes, email changes and two-factor changes.",
          "Press Show older to see more.",
        ],
        links: [{label: "Security log", to: "/account/security/log"}],
      },
      {
        id: "security-wasnt-me",
        title: "What to do about a change you did not make",
        audience: "everyone",
        body: [
          "The site emails you each time your password, email address or two-factor changes. If it was not you, use the lock link in that email. The link works for 72 hours.",
          "Locking signs everyone out and stops anyone from signing in. It does not undo the change.",
          "To get back in, contact the board. An admin checks it is you and unlocks the account.",
        ],
      },
    ],
  },
  {
    id: "recovery",
    title: "Account recovery",
    topics: [
      {
        id: "recovery-forgot-password",
        title: "Reset a forgotten password",
        audience: "everyone",
        body: [
          "On the sign-in page, choose Forgot password? Fill in your username and press Send reset mail.",
          "The email has a link that works for 24 hours. Open it to set a new password there.",
          "You are signed out everywhere.",
        ],
        links: [{label: "Reset your password", to: "/login/forgor"}],
      },
      {
        id: "recovery-lost-phone",
        title: "Get back in if you lost your phone",
        audience: "everyone",
        body: [
          "Sign in with one of your backup codes. On the code step, choose Use a backup code.",
          "Then go to Two-factor authentication and press Replace authenticator app.",
          "If you have no backup codes left, ask the board. An admin checks it is you and resets two-factor.",
          "You get an email with a link that works for 24 hours. Open it and sign in with your username and password. Then set two-factor up again.",
        ],
      },
      {
        id: "recovery-locked",
        title: "Unlock your account",
        audience: "everyone",
        body: [
          "A locked account cannot sign in. Contact the board by email at board@blueshell.utwente.nl. You can also post in #board-questions or #sitecie on our Discord.",
          "An admin checks it is you and unlocks it. You then get an email to set a new password.",
        ],
      },
      {
        id: "recovery-not-confirmed",
        title: "Get a new confirmation email",
        audience: "everyone",
        body: [
          "A new account works only after you open the link in the confirmation email. That link works for 24 hours.",
          "To get a new one, type your username on the sign-in page. Choose Didn't get your confirmation mail? Then press Send a new confirmation email.",
        ],
        links: [{label: "Confirmation email", to: "/login/confirm"}],
      },
      {
        id: "recovery-board-manage",
        title: "Help someone back into their account",
        audience: "board",
        body: [
          "Manage account recovery has three lists. You can search each one.",
          "Inactive accounts are people who never confirmed their account. Press Resend Activation or Resend Member Activation to send the confirmation email again.",
          "Active accounts: press Send Password Reset to send the person a link to set a new password.",
          "Deleted users: press Restore User to bring a deleted account back. Each row shows how many days are left.",
          "You see a preview of each email before it is sent.",
        ],
        links: [{label: "Manage account recovery", to: "/recovery/manage"}],
      },
      {
        id: "recovery-admin-security",
        title: "Unlock an account or reset two-factor",
        audience: "admin",
        body: [
          "In Manage users, press the Account security icon on a person's row. It opens their security log and three actions. Each one needs a reason.",
          "Use Unlock to let a locked account sign in again. The person gets an email to set a new password. If they had two-factor on, they also set that up again.",
          "Use Reset two-factor for someone with no phone and no backup codes. They get a link to set it up again.",
          "Use Resend re-enrolment link to send that link again. It works for 24 hours.",
          "If the person's email address is wrong, correct it in the same dialog first. You cannot do any of this to your own account. Check that it is really the person before you do it.",
        ],
        links: [{label: "Manage users", to: "/user-manager"}],
      },
    ],
  },
  {
    id: "membership",
    title: "Membership",
    topics: [
      {
        id: "membership-join",
        title: "Become a member",
        audience: "everyone",
        body: [
          "Sign up on the Membership page. You do not need a Union Card.",
          "The form has three steps: Your details, Address and Membership. Press Complete Membership at the end.",
          "A regular membership runs from 1 September to 31 August. It continues each year until it is cancelled.",
        ],
        links: [{label: "Membership", to: "/membership"}],
      },
      {
        id: "membership-pending",
        title: "Your membership is pending",
        audience: "everyone",
        body: [
          "After you sign up, your membership is pending. While it is pending, you cannot sign up for members-only events.",
          "You become a member when your first contribution is paid. The treasurer must record the payment.",
          "Payment details shows what you owe and how to pay.",
        ],
        links: [{label: "Payment details", to: "/account/payment-details"}],
      },
      {
        id: "membership-cancel",
        title: "Cancel your membership",
        audience: "member",
        body: [
          "You cannot end your membership on the site. Tell the board no later than four weeks before the new academic year starts. The board ends it for you.",
          "When your membership ends, you also lose your seats on committees.",
        ],
      },
    ],
  },
  {
    id: "contributions",
    title: "Contributions",
    topics: [
      {
        id: "contributions-fee",
        title: "What you pay",
        audience: "everyone",
        body: [
          "The contribution is the yearly membership fee. There are three fees: the full year fee, the half year fee and the alumni fee.",
          "The half year fee is for people who join after the half year cut-off date. The Membership page shows the current amounts.",
        ],
        links: [{label: "Membership", to: "/membership"}],
      },
      {
        id: "contributions-pay",
        title: "How you pay",
        audience: "member",
        body: [
          "You do not pay on the site. Payment details lists your contributions, shows which are unpaid and explains how to pay: by bank transfer, in cash or by direct debit.",
          "The board also sends you an email. It is a payment request with a due date. Or it tells you the date the amount is taken from your bank account by direct debit (incasso).",
          "The treasurer marks your contribution as paid when the money arrives. To start or stop paying by direct debit, email the board.",
        ],
        links: [{label: "Payment details", to: "/account/payment-details"}],
      },
      {
        id: "contributions-periods",
        title: "Contribution periods",
        audience: "treasurer",
        body: [
          "A contribution period is one membership year with its fees. The Contribution Periods list is at the top of Manage users. The plus button adds a period.",
          "Each period has a button to change it. You can change the start and end date, the half year cut-off date, the half year fee, the full year fee and the alumni fee.",
          "Anyone whose membership starts after the cut-off date owes the half year fee for that period.",
        ],
        links: [{label: "Manage users", to: "/user-manager"}],
      },
      {
        id: "contributions-paid",
        title: "Mark people as paid",
        audience: "treasurer",
        body: [
          "In Manage users, pick the contribution period first. Tick the people. Open the User actions menu and choose Mark as paid or Mark as unpaid.",
          "You can also switch the paid mark for one person on their row.",
          "A pending member becomes a member when their first contribution is marked as paid.",
        ],
        links: [{label: "Manage users", to: "/user-manager"}],
      },
      {
        id: "contributions-emails",
        title: "Send payment emails",
        audience: "treasurer",
        body: [
          "Payment emails ask members to pay or tell them when a direct debit will happen.",
        ],
        steps: [
          "In Manage users, pick the contribution period.",
          "Tick the people who should get the email.",
          "Open the User actions menu and choose Send payment emails.",
          "Check who gets an email on the Members step.",
          "Choose the email type and the fee type on Fees & emails. Fill in the payment due date or the debit date.",
          "Check What will be sent, then press Send. Sent emails cannot be taken back.",
        ],
        links: [{label: "Manage users", to: "/user-manager"}],
      },
    ],
  },
  {
    id: "events",
    title: "Events",
    topics: [
      {
        id: "events-sign-up",
        title: "Sign up for an event",
        audience: "everyone",
        body: [
          "Open the event from Upcoming events. Press Sign up. Some events ask you a few questions first.",
          "Add to my calendar downloads the event to your calendar app. You can change your answers or withdraw until the event ends.",
        ],
        links: [{label: "Upcoming events", to: "/events"}],
      },
      {
        id: "events-cannot-sign-up",
        title: "Why you cannot sign up",
        audience: "everyone",
        body: [
          "The event page tells you why. The event may be full, sign-ups may have closed or the event may have started. Or the board has not approved it yet.",
          "Some events are for members only. A pending membership does not count as a membership yet.",
        ],
      },
      {
        id: "events-create",
        title: "Add an event for your committee",
        audience: "committee",
        body: [
          "Anyone in a committee can add events for that committee. Press Add an event on the Upcoming events page or on your committee's page.",
          "Fill in the name, place, start and end, your committee and a description.",
          "You can also add a poster, prices, a sign-up form, a date when sign-ups close and a limit on places. Tick Members only to keep non-members out.",
          "A new event is not public yet. The board has to approve it first. Until then, only the board and your committee can see it.",
        ],
        links: [{label: "Add an event", to: "/events/create"}],
      },
      {
        id: "events-edit",
        title: "Change or delete an event",
        audience: "committee",
        body: [
          "Open the event and press Edit event. If you change an event the board has already approved, it waits for approval again.",
          "Press Delete event to remove the event and all its sign-ups. This cannot be undone.",
        ],
      },
      {
        id: "events-roster",
        title: "See who signed up",
        audience: "committee",
        body: [
          "On your event, press Sign-ups. The page lists everyone who signed up, with their answers.",
          "Use Search attendees to find a person by name, Discord name, email address or phone number. Export as CSV downloads the list.",
        ],
      },
      {
        id: "events-approve",
        title: "Approve an event",
        audience: "board",
        body: [
          "Open the event. Press the button that says Awaiting approval. That approves the event.",
          "The site first asks when the announcement goes out on Discord. The choices are Post now or the next morning.",
          "After that, the button says Approved. Press it again to remove the approval.",
          "When a committee member changes an approved event, the button says Awaiting re-approval. Approve it again.",
        ],
      },
      {
        id: "events-board-sign-ups",
        title: "Change someone's sign-up",
        audience: "board",
        body: [
          "On an event's Sign-ups page, you can use Add sign-up, Edit sign-up and Remove sign-up for any person. Tick Email them about it to tell the person.",
        ],
      },
    ],
  },
  {
    id: "committees",
    title: "Committees",
    topics: [
      {
        id: "committees-join",
        title: "Join a committee",
        audience: "member",
        body: [
          "Every committee has its own page under Committees. To join one, ask the board. The board adds you to the committee.",
          "Once you are in a committee, you are an active member. You can add events for it and edit its page.",
        ],
        links: [{label: "Committees", to: "/committees"}],
      },
      {
        id: "committees-edit",
        title: "Edit your committee's page",
        audience: "committee",
        body: [
          "On your committee's page, press Edit committee. You can change the description, the banner and the games. Only the board can change the committee's name, its web address and its members.",
        ],
      },
      {
        id: "committees-manage",
        title: "Add a committee or change its members",
        audience: "board",
        body: [
          "Press Add a committee on the Committees page. On a committee's edit page, press Add somebody to add a person to the committee. Use Role to set what they do there.",
          "Archive removes a committee from the list of running committees. It does not delete the committee. Press Bring it back to undo that.",
          "Anyone whose membership ends loses their committee seats automatically.",
        ],
        links: [{label: "Committees", to: "/committees"}],
      },
      {
        id: "committees-delete",
        title: "Delete a committee",
        audience: "admin",
        body: [
          "Only an admin can delete a committee. Delete committee asks you which other committee will take over its events.",
        ],
      },
    ],
  },
  {
    id: "esports",
    title: "Esports",
    topics: [
      {
        id: "esports-handles",
        title: "Set your game handles",
        audience: "everyone",
        body: [
          "A handle is your name in a game. On the Esports Teams tab, type a handle for each game you play, then press Save. The esports pages list you by that handle on every team you play in.",
          "To remove a handle, empty the field and save.",
        ],
        links: [{label: "Esports Teams", to: "/account/games"}],
      },
      {
        id: "esports-name",
        title: "Show or hide your real name",
        audience: "everyone",
        body: [
          "The esports pages show your handle. Show my name adds your real name beside it. Hide my name takes it off again.",
          "You decide. It stays off until you turn it on.",
        ],
        links: [{label: "Esports Teams", to: "/account/games"}],
      },
      {
        id: "esports-rosters",
        title: "Teams you played in",
        audience: "everyone",
        body: [
          "The Esports Teams tab lists every season and team you were on. You cannot add yourself to a team. The board puts players on teams.",
        ],
      },
      {
        id: "esports-manage",
        title: "Manage seasons, teams and games",
        audience: "board",
        body: [
          "Only the board changes the esports pages. On a game's page, Add a season starts a new season. Add a team adds a team to it.",
          "A player is found by their handle. So a player needs a handle for that game first.",
          "You can remove a team from one season, or delete the team for good.",
          "The board also adds, changes and archives the games on the Esports and Casual pages.",
        ],
        links: [{label: "Esports", to: "/esports"}, {label: "Casual", to: "/casual"}],
      },
    ],
  },
  {
    id: "association",
    title: "Association pages",
    topics: [
      {
        id: "association-board-page",
        title: "Manage the Board page",
        audience: "board",
        body: [
          "The Board page shows every board year with its members. Add a board starts a new year. Add a member adds a person to it with their role and portrait.",
          "Remove the member and Remove the board take them off again.",
        ],
        links: [{label: "Board", to: "/board"}],
      },
    ],
  },
  {
    id: "people",
    title: "Members and accounts",
    topics: [
      {
        id: "people-find",
        title: "Find a person",
        audience: "board",
        body: [
          "Manage users lists every account. Use Search users to find a person. You can also filter on Membership, Paid, Incasso, Member in period and Discord.",
          "The Paid and Member in period filters use the contribution period you picked at the top.",
        ],
        links: [{label: "Manage users", to: "/user-manager"}],
      },
      {
        id: "people-profile",
        title: "Change somebody's details",
        audience: "board",
        body: [
          "Edit profile on a person's row opens their details. You can change their name, username and email address. The person cannot change these.",
          "Add user in the User actions menu makes an account for somebody.",
        ],
      },
      {
        id: "people-memberships",
        title: "Start or end a membership",
        audience: "board",
        body: [
          "Manage memberships on a person's row shows all their memberships. Add membership starts one. End stops one on a date.",
          "Resume takes the end date off again. Delete removes a membership that was a mistake.",
          "For several people at once, tick them. Then open the User actions menu and choose Start membership or End membership.",
          "A membership has a type: regular, alumni or honorary. Tick Incasso when the member pays by direct debit.",
        ],
      },
      {
        id: "people-delete",
        title: "Delete an account",
        audience: "board",
        body: [
          "Delete user on a person's row removes the account. For 90 days you can bring it back with Restore User in Manage account recovery. After that it is gone for good.",
        ],
        links: [{label: "Manage account recovery", to: "/recovery/manage"}],
      },
    ],
  },
  {
    id: "emails",
    title: "Emails the site sends",
    topics: [
      {
        id: "emails-outbox",
        title: "Check whether an email arrived",
        audience: "board",
        body: [
          "Manage emails lists every email the site sent, with its status: pending, sent, delivered, opened, bounced or failed. Search by the person's address or the subject. Preview shows the email as it was sent.",
          "Retry sends a failed email again. A bounced email means the address does not work. Correct the person's email address first.",
        ],
        links: [{label: "Manage emails", to: "/management/emails"}],
      },
    ],
  },
  {
    id: "site",
    title: "Running the site",
    topics: [
      {
        id: "site-roles",
        title: "Add a role to somebody",
        audience: "admin",
        body: [
          "In Manage users, Edit roles on a person's row sets Board, Treasurer or Admin. Member and committee roles are not set here. They follow from a membership and a committee seat.",
          "A new role does not work until the person has two-factor on. The site sends them to set up two-factor the next time they open a page.",
        ],
        links: [{label: "Manage users", to: "/user-manager"}],
      },
      {
        id: "site-jobs",
        title: "See and run background jobs",
        audience: "admin",
        body: [
          "Jobs are the tasks the site runs by itself, such as sending emails and keeping contacts up to date. Manage jobs lists each run with its status.",
          "Retry runs a failed job again. Trigger job starts one by hand.",
        ],
        links: [{label: "Manage jobs", to: "/management/jobs"}],
      },
      {
        id: "site-cohorts",
        title: "Check cohorts and their mailing lists",
        audience: "admin",
        body: [
          "A cohort is a group of people the site keeps up to date, such as the members of a year or of a committee. Each cohort is copied to a list in Brevo, the mailing service.",
          "Manage cohorts shows each cohort, its list and the people who are in one but not the other. Reconcile now compares a cohort with its list again.",
        ],
        links: [{label: "Manage cohorts", to: "/management/cohorts"}],
      },
    ],
  },
  {
    id: "roles",
    title: "Roles",
    topics: [
      {
        id: "roles-what",
        title: "What you can do depends on your role",
        audience: "everyone",
        body: [
          "Account: everybody who signed up. You can sign up for open events and keep your own details up to date.",
          "Member: you have a membership and your first contribution is paid. You can sign up for members-only events.",
          "Active member: you are in a committee. You can add events for that committee and edit its page.",
          "Board: you approve events and manage members, committees and the esports pages.",
          "Treasurer: you can do everything the board can. You also manage contributions: the periods, who has paid and the payment emails.",
          "Admin: the website committee (sitecie). Admins add roles, unlock accounts and look after the site itself.",
        ],
      },
      {
        id: "roles-two-factor",
        title: "Why a role asks for two-factor",
        audience: "everyone",
        body: [
          "Board, treasurer and admin roles work only with two-factor on. If you get one of these roles without it, every page sends you to set up two-factor first. You cannot turn two-factor off while you hold the role.",
        ],
        links: [{label: "Two-factor authentication", to: "/account/security/two-factor"}],
      },
    ],
  },
  {
    id: "contact",
    title: "Still stuck?",
    topics: [
      {
        id: "contact-board",
        title: "Ask the board or the sitecie",
        audience: "everyone",
        body: [
          "Email the board at board@blueshell.utwente.nl, or post in #board-questions on our Discord.",
          "Is something on the website broken? Post in #sitecie. The sitecie is the committee that builds the website.",
        ],
      },
    ],
  },
]
