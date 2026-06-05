export interface AIConfig {
  baseUrl: string;
  apiKey: string;
  model: string;
}

export interface YApiConfig {
  baseUrl: string;
  token: string;
  projectId?: number;
}

export interface Project {
  id: string;
  name: string;
  description?: string;
  yapi: YApiConfig;
  ai: AIConfig;
  /** 项目源码在服务器上的绝对路径，用于 AI 读取代码上下文 */
  codePath: string;
  /** 开发环境后端基础地址，例如 http://localhost:8080 */
  devBaseUrl: string;
  /** 开发环境前端地址，例如 http://localhost:3000 */
  devWebUrl: string;
  /** 一键启动命令，例如 npm run dev */
  startCommand: string;
  createdAt: number;
  updatedAt: number;
}

export interface ApiParam {
  name: string;
  required: boolean;
  type: string;
  desc?: string;
  example?: string;
}

export interface ApiInterface {
  id: string;
  title: string;
  path: string;
  method: string;
  catName?: string;
  status?: string;
  reqParams: ApiParam[];   // path 参数
  reqQuery: ApiParam[];    // query 参数
  reqHeaders: ApiParam[];  // header
  /** 请求体 JSON Schema（字符串）或表单字段 */
  reqBody?: any;
  reqBodyType?: 'json' | 'form' | 'none';
  /** 响应体 JSON Schema，用于 AI 校验 */
  resBody?: any;
  updatedAt?: number;
}

export interface FilledRequest {
  pathParams: Record<string, any>;
  query: Record<string, any>;
  headers: Record<string, any>;
  body: any;
  reasoning?: string;
}

export interface TestRecord {
  id: string;
  projectId: string;
  interfaceId: string;
  title: string;
  method: string;
  url: string;
  request: FilledRequest;
  response: {
    ok: boolean;
    status: number;
    statusText: string;
    durationMs: number;
    headers: Record<string, string>;
    body: any;
    error?: string;
  };
  analysis?: {
    passed: boolean;
    score: number;
    summary: string;
    issues: string[];
  };
  source: 'manual' | 'auto';
  createdAt: number;
}

export interface AppData {
  projects: Project[];
  tests: TestRecord[];
}
