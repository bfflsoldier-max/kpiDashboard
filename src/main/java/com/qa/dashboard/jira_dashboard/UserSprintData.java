package com.qa.dashboard.jira_dashboard;

public class UserSprintData {

    private String name;
    private int total;
    private int todo;
    private int inQa;
    private int review;
    private int blocked;
    private int done;
    private int bugs;
    private int stories;

    public UserSprintData(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getDone() {
        return done;
    }

    public void setDone(int done) {
        this.done = done;
    }

    public int getInQa() {
        return inQa;
    }

    public void setInQa(int inQa) {
        this.inQa = inQa;
    }

    public int getReview() {
        return review;
    }

    public void setReview(int review) {
        this.review = review;
    }

    public int getBlocked() {
        return blocked;
    }

    public void setBlocked(int blocked) {
        this.blocked = blocked;
    }

    public int getTodo() {
        return todo;
    }

    public void setTodo(int todo) {
        this.todo = todo;
    }

    public int getBugs() {
        return bugs;
    }

    public void setBugs(int bugs) {
        this.bugs = bugs;
    }

    public int getStories() {
        return stories;
    }

    public void setStories(int stories) {
        this.stories = stories;
    }
}
