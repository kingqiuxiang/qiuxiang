import type { TopicInput } from './topic.js';

type ParsedLine = {
  level: number;
  title: string;
};

const BULLET_PATTERN = /^(\s*)(?:[-*+]|\d+[.)])\s+(.+)$/;
const HEADING_PATTERN = /^(#{1,6})\s+(.+)$/;

export function parseOutline(outline: string | undefined): TopicInput[] {
  if (!outline) {
    return [];
  }

  const lines = outline
    .split(/\r?\n/)
    .map(parseLine)
    .filter((line): line is ParsedLine => Boolean(line));

  const root: TopicInput = { title: '__root__', children: [] };
  const stack: TopicInput[] = [root];

  for (const line of lines) {
    while (stack.length > line.level) {
      stack.pop();
    }

    const parent = stack[stack.length - 1] ?? root;
    parent.children ??= [];

    const topic: TopicInput = { title: line.title, children: [] };
    parent.children.push(topic);
    stack[line.level] = topic;
  }

  return root.children ?? [];
}

function parseLine(line: string): ParsedLine | null {
  if (!line.trim()) {
    return null;
  }

  const heading = line.trim().match(HEADING_PATTERN);
  if (heading) {
    return {
      level: heading[1].length,
      title: cleanTitle(heading[2]),
    };
  }

  const bullet = line.match(BULLET_PATTERN);
  if (bullet) {
    const indent = bullet[1].replace(/\t/g, '  ').length;
    return {
      level: Math.floor(indent / 2) + 1,
      title: cleanTitle(bullet[2]),
    };
  }

  return {
    level: 1,
    title: cleanTitle(line),
  };
}

function cleanTitle(value: string): string {
  return value
    .replace(/^\[[ xX]\]\s+/, '')
    .replace(/\s+#\w[\w-]*$/g, '')
    .trim();
}
