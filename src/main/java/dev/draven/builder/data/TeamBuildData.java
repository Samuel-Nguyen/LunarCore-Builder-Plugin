package dev.draven.builder.data;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TeamBuildData {
    private String name;
    private String url;
    private String note;

    private List<CharacterBuildData> buildData;
}
