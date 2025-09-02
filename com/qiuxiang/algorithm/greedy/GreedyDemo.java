package com.qiuxiang.algorithm.greedy;

import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * 贪心算法
 * 它是一种在每一步选择中都采取在当前状态下最好或最优（即最有利）的选择，从而希望导致结果是全局最好或最优的算法。
 * 贪心算法在每一步的选择上都做出局部最优的选择，而不考虑整体的最优性。
 * 贪心算法适用于那些具有最优子结构性质的问题，即一个问题的最优解包含其子问题的最优解。
 * 贪心算法通常用于解决一些组合优化问题，如最小生成树、单源最短路径、活动选择问题等。
 * 贪心算法的优点是简单高效，通常具有较低的时间复杂度；缺点是不能保证总是能得到全局最优解。
 * 贪心算法的核心在于选择策略，即如何在每一步选择中做出最优选择。
 * 常见的贪心算法包括Dijkstra算法、Prim算法、Kruskal算法等。
 * 贪心算法的设计步骤通常包括以下几个方面：
 * 1. 定义问题的选择策略，即在每一步选择中如何做出最优选择。
 * 2. 证明贪心选择性质，即通过贪心选择可以得到问题的最优解。
 * 3. 证明最优子结构性质，即问题的最优解包含其子问题的最优解。
 * 4. 设计算法并分析其时间复杂度和空间复杂度。
 */
public class GreedyDemo {
    /**
     * 给一个str,拼接所有字符串,返回所有可能的拼接结果中,字典序最小的字符串
     * 例如:fgacde,应当返回acdefg
     * 思路:贪心算法,每次选择字典序最小的字符
     *
     * @param str
     */

    public String findMinString(String str) {
        if (str == null || str.length() == 0) {
            return "";
        }
        char[] chars = str.toCharArray();
        StringBuilder sb = new StringBuilder();
        boolean[] visited = new boolean[chars.length];
        for (int i = 0; i < chars.length; i++) {
            char minChar = Character.MAX_VALUE;
            int minIndex = -1;
            for (int j = 0; j < chars.length; j++) {
                if (!visited[j] && chars[j] < minChar) {
                    minChar = chars[j];
                    minIndex = j;
                }
            }
            sb.append(minChar);
            visited[minIndex] = true;
        }
        return sb.toString();
    }

    /**
     * 给一个str的数组,拼接所有字符串,返回所有可能的拼接结果中,字典序最小的字符串
     * 例如:fgacde,应当返回acdefg
     * 思路:贪心算法,每次选择字典序最小的字符
     *
     * @param str
     */
    public static String findMinString(String[] str) {
        if (str == null || str.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean[] visited = new boolean[str.length];
        for (int i = 0; i < str.length; i++) {
            String s = str[i];
            int minChars = Integer.MAX_VALUE;
            int minIndex = -1;
            for (int j = 0; j < str.length; j++) {
                if (!visited[j] && calculateChars(s + str[j]) < minChars) {
                    minChars = calculateChars(s + str[j]);
                    minIndex = j;
                }
                sb.append(str[j]);
                visited[minIndex] = true;
            }
        }
        return sb.toString();
    }

    private static int calculateChars(String s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        int sum = 0;

        for (char c : s.toCharArray()) {
            sum = sum + c;
        }
        return sum;
    }

    /**
     * 给一个str的数组,拼接所有字符串,返回所有可能的拼接结果中,字典序最小的字符串
     * 例如:fgacde,应当返回acdefg
     * 思路:贪心算法,每次选择字典序最小的字符
     *
     * @param str
     */
    public static String findMinString2(String[] str) {
        if (str == null || str.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        /**
         * 只需要排序,保证,每一次最小字典序在前,一定会获得最小的拼接方案
         */
        Arrays.sort(str, (a, b) -> (a + b).compareTo(b + a));
        for (String s : str) {
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * 一块金条切成两半,是需要花费和长度数值一样的铜板的。比如长度为20的金条,不管怎么切,都要花费20个铜板。一群人想整份整块金条,怎么分最省铜板?
     * 例如,给定数组{10,20,30},代表一共三个人,整块金条长度为60.金条要分成10,20,30三部分。 如果先把60切成10和50,花费60;再把50切成20和30,花费50;一共花费110铜板。但如果先把60切成30和30,花费60;再把30切成10和20,花费30;一共花费90铜板。输入一个数组,返回分割的最小代价。
     * 思路:贪心算法,每次选择最小的两个数进行合并
     */
    public int getMinCost(int[] arr) {
        if (arr == null || arr.length == 0) {
            return 0;
        }
        PriorityQueue<Integer> heap = new PriorityQueue<>();
        for (int num : arr) {
            heap.offer(num);
        }
        int totalCost = 0;
        while (heap.size() > 1) {
            int first = heap.poll();
            int second = heap.poll();
            int cost = first + second;
            heap.offer(cost);
            totalCost += cost;
        }
        return totalCost;

    }
    /**
     * 给定一个数组, k树之和为0,返回所有不重复的三元组
     */

    public int[] process(int[] arr) {
        if (arr == null || arr.length < 3) {
            return new int[0];
        }
        Arrays.sort(arr);
        for (int i = 0; i < arr.length - 2; i++) {
            if (i > 0 && arr[i] == arr[i - 1]) {
                continue;
            }
            int left = i + 1;
            int right = arr.length - 1;
            while (left < right) {
                int sum = arr[i] + arr[left] + arr[right];
                if (sum == 0) {
                    return new int[]{arr[i], arr[left], arr[right]};
                } else if (sum < 0) {
                    left++;
                } else {
                    right--;
                }
            }
        }
        return new int[0];
    }

    /**
     * 给定一个成本 w,此时,对于每一笔交易都存在着花费及收益率,以两个数组的方式进行提供,此时求获得的最大收益
     * 核心就是满足成本条件下的最大的收益的交易优先做
     */

    public int maxProfit(int[] costs, int[] profits, int w) {
        if (costs == null || profits == null || costs.length == 0 || profits.length == 0 || costs.length != profits.length) {
            return w;
        }
        int n = costs.length;
        boolean[] visited = new boolean[n];
        PriorityQueue<Integer> minCostHeap = new PriorityQueue<>((a, b) -> (costs[a] - costs[b]));
        PriorityQueue<Integer> maxProfitHeap = new PriorityQueue<>((a, b) -> (profits[b] - profits[a]));
        for (int i = 0; i < n; i++) {
            minCostHeap.offer(i);
        }
        while (!minCostHeap.isEmpty() && costs[minCostHeap.peek()] <= w) {
            int index = minCostHeap.poll();
            maxProfitHeap.offer(index);
        }
        while (!maxProfitHeap.isEmpty()) {
            int index = maxProfitHeap.poll();
            w += profits[index];
            visited[index] = true;
            while (!minCostHeap.isEmpty() && costs[minCostHeap.peek()] <= w) {
                int nextIndex = minCostHeap.poll();
                if (!visited[nextIndex]) {
                    maxProfitHeap.offer(nextIndex);
                }
            }
        }
        return w;
    }


}