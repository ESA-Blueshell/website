/**
 * The help domain's public API: what a component may reach for, and nothing else
 * (frontend ADR-001). Re-exported by name, because the list of names is the promise.
 */
export {audiencesIn, helpAbout, helpFor, searchHelp, type Audience, type HelpLink, type HelpReader, type HelpSection, type HelpTopic} from "./help"
export {HELP} from "./topics"
