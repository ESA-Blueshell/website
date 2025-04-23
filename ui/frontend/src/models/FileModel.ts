import type BaseModel from "@/models/BaseModel";
import type {FileType} from "@/models/enums/FileType";


export default interface FileModel extends BaseModel {
  id?: number;
  name?: string;
  url?: string;
  uploaderId?: number;
  createdAt?: string;
  mediaType?: string;
  size?: number;
  fileName?: string;
  fileType?: FileType;
  base64Content?: string;
}

export const defaultFile: FileModel = {
  type: 'FileDTO',
  id: undefined,
  name: '',
  url: '',
  uploaderId: undefined,
  createdAt: '',
  mediaType: '',
  size: undefined,
  fileName: '',
  fileType: undefined,
  base64Content: '',
};
