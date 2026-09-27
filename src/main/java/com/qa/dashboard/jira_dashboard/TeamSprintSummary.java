package com.qa.dashboard.jira_dashboard;

import java.util.List;

public class TeamSprintSummary {

    private String team;
    private int total;
    private int todo;
    private int inQa;
    private int review;
    private int blocked;
    private int done;
    private int bugs;
    private double defectDensity;
    private int stories;

    private List<UserSprintData> users;

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getTodo() {
        return todo;
    }

    public void setTodo(int todo) {
        this.todo = todo;
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

    public int getDone() {
        return done;
    }

    public void setDone(int done) {
        this.done = done;
    }

    public int getBugs() {
        return bugs;
    }

    public void setBugs(int bugs) {
        this.bugs = bugs;
    }

    public List<UserSprintData> getUsers() {
        return users;
    }

    public void setUsers(List<UserSprintData> users) {
        this.users = users;
    }

    public double getDefectDensity() {
        return defectDensity;
    }

    public void setDefectDensity(double defectDensity) {
        this.defectDensity = defectDensity;
    }

    public int getStories() {
        return stories;
    }

    public void setStories(int stories) {
        this.stories = stories;
    }
}
