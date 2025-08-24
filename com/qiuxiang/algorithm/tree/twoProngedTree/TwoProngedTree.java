package com.qiuxiang.algorithm.tree.twoProngedTree;

import com.qiuxiang.algorithm.nodes.Node;

import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

public class TwoProngedTree {
    public int root;
    public TwoProngedTree left;
    public TwoProngedTree right;

    public TwoProngedTree() {

    }

    public TwoProngedTree(int root, TwoProngedTree left, TwoProngedTree right) {
        this.root = root;
        this.left = left;
        this.right = right;
    }

    public TwoProngedTree(int root) {
        this.root = root;
    }


    public static void insert(TwoProngedTree root, int data) {
        if (root == null) {
            return;
        }
        if (data < root.root) {
            if (root.left == null) {
                root.left = new TwoProngedTree(data);
            } else {
                insert(root.left, data);
            }
        } else if (data > root.root) {
            if (root.right == null) {
                root.right = new TwoProngedTree(data);
            } else {
                insert(root.right, data);
            }
        }
    }

    //    前序遍历
    public static void preOrder(TwoProngedTree root) {
        if (root == null) {
            return;
        }
        System.out.print(root.root + " ");
        preOrder(root.left);
        preOrder(root.right);
    }

    public static void in(TwoProngedTree root) {
        if (root == null) {
            return;
        }
        in(root.left);
        System.out.print(root.root + " ");
        in(root.right);
    }

    public static void last(TwoProngedTree root) {
        if (root == null) {
            return;
        }
        last(root.left);
        last(root.right);
        System.out.print(root.root + " ");
    }

    /**
     * 递归序
     *
     * @param args
     */
    public static void recursion(TwoProngedTree root) {
        if (root == null) {
            return;
        }
        System.out.print(root.root + " ");
        recursion(root.left);
        System.out.print(root.root + " ");
        recursion(root.right);
        System.out.print(root.root + " ");
    }

    public static void layer(TwoProngedTree root) {
        Queue<TwoProngedTree> queue = new LinkedList<>();
        if (root == null) {
            return;
        }
        queue.add(root);
        while (!queue.isEmpty()) {
            TwoProngedTree current = queue.poll();
            System.out.print(current.root + " ");
            if (current.left != null) {
                queue.add(current.left);
            }
            if (current.right != null) {
                queue.add(current.right);
            }
        }

    }

    public static int max_weight(TwoProngedTree root) {
        Queue<TwoProngedTree> queue = new LinkedList<>();
        if (root == null) {
            return 0;
        }
        Map<TwoProngedTree, Integer> map = new java.util.HashMap<>();
        queue.add(root);
        int curLevelNodes = 0;
        int max = Integer.MIN_VALUE;
        map.put(root, 1);
        int level = map.get(queue.peek());
        while (!queue.isEmpty()) {

            TwoProngedTree current = queue.poll();
            int curLevel = map.get(current);
            if (curLevel == level) {
                curLevelNodes++;
            } else {
                max = Math.max(max, curLevelNodes);
                level = curLevel;
                curLevelNodes = 1;
            }

            System.out.print(current.root + " ");
            if (current.left != null) {
                queue.add(current.left);
                map.put(current.left, curLevel + 1);
            }
            if (current.right != null) {
                queue.add(current.right);
                map.put(current.right, curLevel + 1);
            }
        }
        return Math.max(max, curLevelNodes);

    }

    public static int max_weight2(TwoProngedTree root) {
        Queue<TwoProngedTree> queue = new LinkedList<>();
        if (root == null) {
            return 0;
        }

        queue.add(root);
        int curLevelNodes = 0;
        int max = Integer.MIN_VALUE;
        TwoProngedTree curRightEnd = root;
        TwoProngedTree nextRightEnd = null;
        while (!queue.isEmpty()) {

            TwoProngedTree current = queue.poll();

            System.out.print(current.root + " ");
            if (current.left != null) {
                queue.add(current.left);
            }
            if (current.right != null) {
                queue.add(current.right);
                nextRightEnd = current.right;
            }

            if (current == curRightEnd) {
                max = Math.max(max, ++curLevelNodes);
                curLevelNodes = 0;
                curRightEnd = nextRightEnd;
            } else {
                curLevelNodes++;
            }

        }
        return max;
    }

    public static Queue preSerial(TwoProngedTree root) {
        Queue<TwoProngedTree> queue = new LinkedList<>();
        pres(root, queue);
        return queue;
    }

    public static Queue inSerial(TwoProngedTree root) {
        Queue<TwoProngedTree> queue = new LinkedList<>();
        ins(root, queue);
        return queue;
    }

    public static void ins(TwoProngedTree root, Queue<TwoProngedTree> queue) {
        if (root == null) {
            queue.add(null);
            return;
        }
        ins(root.left, queue);
        queue.add(root);
        ins(root.right, queue);
    }

    public static TwoProngedTree preDeSerial(Queue root) {
        if (root == null) {
            return null;
        }
        TwoProngedTree twoProngedTree = preDes(root);

        return twoProngedTree;
    }

    private static TwoProngedTree preDes(Queue root) {
        TwoProngedTree current = (TwoProngedTree) root.poll();
        if (current == null) {
            return null;
        }
        TwoProngedTree twoProngedTree = new TwoProngedTree();
        twoProngedTree.root = current.root;
        //step 1. get left child
        twoProngedTree.left = preDes(root);
        //step 2 get right child
        twoProngedTree.right = preDes(root);
        return twoProngedTree;
        // step3 return current;
    }


    public static void pres(TwoProngedTree root, Queue<TwoProngedTree> queue) {
        if (root == null) {
            queue.add(null);
            return;
        }
        queue.add(root);
        pres(root.left, queue);
        pres(root.right, queue);

    }

    public static TwoProngedTree inDeSerial(Queue root) {
        if (root == null) {
            return null;
        }

        TwoProngedTree twoProngedTree = inDeserial(root);

        return twoProngedTree;
    }

    public static TwoProngedTree inDeserial(Queue<TwoProngedTree> queue) {
        if (queue.isEmpty()) {
            return null;
        }
        TwoProngedTree left = queue.poll();
        TwoProngedTree mid = inDeserial(queue);
        TwoProngedTree right = inDeserial(queue);
        if (mid != null) {
            mid.left = left;
            mid.right = right;
        }
        return mid;
    }

    public static void main(String[] args) {
        TwoProngedTree tree = new TwoProngedTree(10);
        insert(tree, 5);
        insert(tree, 15);
        insert(tree, 3);
        insert(tree, 7);
        insert(tree, 12);
        insert(tree, 18);

//        System.out.println("Root: " + tree.root);
//        System.out.println("Left child of root: " + tree.left.root);
//        System.out.println("Right child of root: " + tree.right.root);
//        System.out.println("Left child of left child: " + tree.left.left.root);
//        layer(tree);
//        System.out.println();
//        System.out.println(max_weight2(tree));
//        in(tree);
        Queue queue = preSerial(tree);
        TwoProngedTree twoProngedTree = preDes(queue);
        queue = inSerial(twoProngedTree);
        inDeSerial(queue);

        in(twoProngedTree);
        System.out.println("--------------------");


//        TwoProngedTree twoProngedTree = preDeSerial(queue);
//        preOrder(twoProngedTree);
//        System.out.println();
        while (!queue.isEmpty()) {
            TwoProngedTree node = (TwoProngedTree) queue.poll();
            if (node == null) {
                System.out.print("null ");
            } else {
                System.out.print(node.root + " ");
            }
        }
        System.out.println();
    }

}
