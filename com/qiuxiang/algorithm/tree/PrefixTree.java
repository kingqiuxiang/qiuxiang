package com.qiuxiang.algorithm.tree;

/**
 * 前缀树（Trie）是一种树形数据结构，用于存储字符串集合，特别适用于前缀查询。
 */
public class PrefixTree {
    public TrieNode root;

    public PrefixTree(TrieNode root) {
        this.root = root;
    }

    public PrefixTree() {
    }

    public static class TrieNode {
        private int passCount; // 经过该节点的字符串数量
        private int endCount; // 以该节点结尾的字符串数量
        private TrieNode[] next; // 子节点数组

        public TrieNode() {
            this.passCount = 0;
            this.endCount = 0;
            this.next = new TrieNode[26]; // 假设只处理小写字母a-z
        }

        public int getPassCount() {
            return passCount;
        }

        public void setPassCount(int passCount) {
            this.passCount = passCount;
        }

        public int getEndCount() {
            return endCount;
        }

        public void setEndCount(int endCount) {
            this.endCount = endCount;
        }

        public TrieNode[] getNext() {
            return next;
        }

        public void setNext(TrieNode[] next) {
            this.next = next;
        }
    }

    /**
     * 插入一个字符串到前缀树中。
     * 如果字符串为空或null，则不进行任何操作。
     *
     * @param word 该方法是用来把一个字符串插入到前缀树中。
     */
    public void insert(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        char[] charArray = word.toCharArray();
        TrieNode node = root;
        node.passCount++;
        for (char c : charArray) {
            int path = c - 'a'; // 假设只处理小写字母a-z
            if (node.next[path] == null) {
                node.next[path] = new TrieNode();
            }
            node = node.next[path];
            node.passCount++;
        }
        node.endCount++;
    }

    /**
     * 搜索一个字符串在前缀树中出现的次数。
     * 如果字符串为空或null，则返回0。
     *
     * @param word
     * @return 该方法是用来搜索一个字符串在前缀树中出现的次数。
     * 其中最关键的是endCount，它表示以该节点结尾的字符串数量。
     * isPrefix 代表是否是前缀查询，如果是前缀查询，则返回经过该节点的字符串数量（passCount），否则返回以该节点结尾的字符串数量（endCount）。
     * 如果isPrefix为true，则返回经过该节点的字符串数量（passCount），
     */
    public int search(String word, boolean isPrefix) {
        if (word == null || word.isEmpty()) {
            return 0;
        }
        char[] charArray = word.toCharArray();
        TrieNode node = root;
        for (char c : charArray) {
            int path = c - 'a'; // 假设只处理小写字母a-z
            if (node.next[path] == null) {
                return 0; // 如果路径不存在，返回0
            }
            node = node.next[path];
        }
        return isPrefix ? node.passCount : node.endCount; // 返回以该字符串结尾的数量
    }

    public void delete(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        char[] charArray = word.toCharArray();
        TrieNode node = root;
        if (search(word, false) == 0) {
            return;
        }
        node.passCount--;
        for (char c : charArray) {
            int path = c - 'a';
            if (node.next[path].passCount == 1) {
                node.next[path] = null;
                return;
            } else {
                node.next[path].passCount--;
                node = node.next[path];
            }
            node.endCount--;

        }
    }
}
