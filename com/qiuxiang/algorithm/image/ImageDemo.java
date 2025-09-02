package com.qiuxiang.algorithm.image;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;

public class ImageDemo {
//点结构的描述,
    // 1.点的值
    // 2.入度
    // 3.出度
    // 4.下一个点的集合
    // 5.边的集合
    // 图结构的描述
    // 1.点的集合
    // 2.边的集合

    //图结构的描述
    //1.点的集合
    //2.边的集合
    public static class MNode {
        public int value;
        public int in;
        public int out;
        public ArrayList<MNode> next;
        public ArrayList<Edge> edges;

        public MNode(int value) {
            this.value = value;
            in = 0;
            out = 0;
            next = new ArrayList<MNode>();
            edges = new ArrayList<Edge>();
        }


        public static class Edge {
            public int weight;
            public MNode from;
            public MNode to;

            public Edge(int weight, MNode from, MNode to) {
                this.weight = weight;
                this.from = from;
                this.to = to;
            }
        }


        public static class Graph {
            public HashMap<Integer, MNode> nodes;
            public HashSet<Edge> edges;

            public Graph() {
                nodes = new HashMap<>();
                edges = new HashSet<>();
            }

            //INDEX 0 代表了 from 1 代表了 to 2 代表了 weight
            public static Graph createGraph(int[][] matrix) {
                Graph graph = new Graph();
                for (int i = 0; i < matrix.length; i++) {
                    int from = matrix[i][0];
                    int to = matrix[i][1];
                    int weight = matrix[i][2];
                    if (!graph.nodes.containsKey(from)) {
                        graph.nodes.put(from, new MNode(from));
                    }
                    if (!graph.nodes.containsKey(to)) {
                        graph.nodes.put(to, new MNode(to));
                    }
                    MNode fromNode = graph.nodes.get(from);
                    MNode toNode = graph.nodes.get(to);
                    Edge newEdge = new Edge(weight, fromNode, toNode);
                    fromNode.next.add(toNode);
                    fromNode.out++;
                    toNode.in++;
                    fromNode.edges.add(newEdge);
                    graph.edges.add(newEdge);
                }
                return graph;
            }

            //            深度优先遍历
            public static void dfs(MNode node, HashSet<MNode> set) {
                if (node == null) {
                    return;
                }
                System.out.println(node.value);
                set.add(node);
                for (MNode next : node.next) {
                    if (!set.contains(next)) {
                        dfs(next, set);
                    }
                }
            }

            //广度优先遍历
            public static void bfs(MNode node) {
                if (node == null) {
                    return;
                }
                HashSet<MNode> set = new HashSet<>();
                Queue<MNode> queue = new java.util.LinkedList<>();
                queue.add(node);
                set.add(node);
                while (!queue.isEmpty()) {
                    MNode cur = queue.poll();
                    System.out.println(cur.value);
                    for (MNode next : cur.next) {
                        if (!set.contains(next)) {
                            set.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
    }


}
