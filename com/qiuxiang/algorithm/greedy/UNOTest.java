package com.qiuxiang.algorithm.greedy;

import com.qiuxiang.algorithm.unionfind.UnionFind;

/**
 * 给定两个数组,第一个牌,第二个是颜色,单人打牌,数字相同颜色相同,牌可以打出,问怎么打牌可以尽可能的打出更多的额牌
 */
public class UNOTest {

    public static int maxCards(int[] cards, String[] colors) {
        int n = cards.length;
        boolean[] used = new boolean[n];
        int maxCount = 0;

        for (int i = 0; i < n; i++) {
            if (used[i]) continue;
            int count = 1;
            used[i] = true;
            int currentCard = cards[i];
            String currentColor = colors[i];

            // 尝试向前打牌
            for (int j = i - 1; j >= 0; j--) {
                if (!used[j] && (cards[j] == currentCard || colors[j].equals(currentColor))) {
                    used[j] = true;
                    count++;
                    currentCard = cards[j];
                    currentColor = colors[j];
                }
            }

            // 尝试向后打牌
            currentCard = cards[i];
            currentColor = colors[i];
            for (int j = i + 1; j < n; j++) {
                if (!used[j] && (cards[j] == currentCard || colors[j].equals(currentColor))) {
                    used[j] = true;
                    count++;
                    currentCard = cards[j];
                    currentColor = colors[j];
                }
            }

            maxCount = Math.max(maxCount, count);
        }

        return maxCount;
    }

    /**
     * 利用并查集处理以上问题
     */
    public int countComponents(int[] cards, String[] colors) {
        int n = cards.length;
        UnionFind uf = new UnionFind(n);

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (cards[i] == cards[j] || colors[i].equals(colors[j])) {
                    uf.union(i, j);
                }
            }
        }

        return uf.getCount();
    }


}
