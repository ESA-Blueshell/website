/** Every channel in the server with who it is opened to, for the Discord pages. */
import {type CataloguedChannel, listCataloguedChannels} from "@/services/api"
import {readOr} from "@/utils/answers"

export type {CataloguedChannel}

/** The server's channels and categories; nothing where they could not be read. */
export const listCatalogue = (): Promise<CataloguedChannel[] | null> => readOr(listCataloguedChannels(), null)
