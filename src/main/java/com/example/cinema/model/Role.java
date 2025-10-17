package com.example.cinema.model;

public class Role {
    private Long id;
    private String code;
    private String name;
    private String description;
    private int level;

    // 預設建構子
    public Role() {
    }

    // 簡單建構子
    public Role(Long id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
    }

    // 完整建構子
    public Role(Long id, String code, String name, String description, int level) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.level = level;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }
}
