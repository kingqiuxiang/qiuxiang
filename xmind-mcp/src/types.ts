export interface TopicNode {
  title: string;
  note?: string;
  labels?: string[];
  children?: TopicNode[];
}

export interface MindMapInput {
  title: string;
  sheetTitle?: string;
  children?: TopicNode[];
}

export interface MindMapTree {
  filePath: string;
  sheets: Array<{
    title: string;
    root: TopicNode;
  }>;
}
