export type TopicInput = {
  title: string;
  notes?: string;
  labels?: string[];
  children?: TopicInput[];
};

export type NormalizedTopic = {
  title: string;
  notes?: string;
  labels?: string[];
  children: NormalizedTopic[];
};

export function normalizeTitle(value: string | undefined, fallback: string): string {
  const normalized = value?.trim();
  return normalized && normalized.length > 0 ? normalized : fallback;
}

export function normalizeTopics(topics: TopicInput[] | undefined): NormalizedTopic[] {
  if (!topics || topics.length === 0) {
    return [];
  }

  return topics
    .map((topic) => normalizeTopic(topic))
    .filter((topic): topic is NormalizedTopic => Boolean(topic));
}

function normalizeTopic(topic: TopicInput | undefined): NormalizedTopic | null {
  const title = normalizeTitle(topic?.title, '');
  if (!title) {
    return null;
  }

  const labels = topic?.labels
    ?.map((label) => label.trim())
    .filter((label) => label.length > 0);

  return {
    title,
    notes: topic?.notes?.trim() || undefined,
    labels: labels && labels.length > 0 ? labels : undefined,
    children: normalizeTopics(topic?.children),
  };
}

export function countTopics(topics: NormalizedTopic[]): number {
  return topics.reduce((total, topic) => total + 1 + countTopics(topic.children), 0);
}

export function sanitizeFileName(value: string): string {
  const safe = value
    .trim()
    .replace(/[<>:"/\\|?*\x00-\x1f]/g, '-')
    .replace(/\s+/g, ' ')
    .replace(/\.+$/g, '')
    .slice(0, 120);

  return safe || 'xmind-mindmap';
}
