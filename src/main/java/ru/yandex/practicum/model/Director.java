package ru.yandex.practicum.model;

import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@Builder
public class Director {

    private int id;

    @NotBlank
    @Size(max = 100)
    private final String name;
}