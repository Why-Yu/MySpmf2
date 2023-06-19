package cn.whyyu.myspmf;

public class Result implements Comparable<Result>{
    public int key;
    public float score;

    public Result(int key, float score) {
        this.key = key;
        this.score = score;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    @Override
    public int compareTo(Result o) {
        return Float.compare(this.score, o.score);
    }

    @Override
    public String toString() {
        return "(" + key + ", " + score + ")";
    }
}
