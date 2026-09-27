/*** Team configuration: maps individual name keywords to their team. Update this object when team membership changes. ***/
const teamMapping = {
    "Aswin": "Data Hub",
    "Manoj": "Data Hub",
    "Priyatosh": "Arc Xp",
    "Aryan, Raj": "Arc Xp",
    "Hugar, Manjunath": "Arc Xp",
    "Nithin": "Content Discovery",
    "Deekshit": "Crave Web",
    "Varadarajan, Srividhya": "Crave Web",
    "Kumar, Lohith": "Crave Web",
    "Aishwarya": "Crave Mobile",
    "Darshan Manohar": "Crave Mobile"
};

/*** Maps a team name to its Jira project key. ***/
function mapTeamToProject(team) {
    if (team === "Data Hub") return "RDSDEV";
    if (team === "Arc Xp") return "BMARC";
    if (team === "Content Discovery") return "BMCD";
    if (team === "Crave Mobile") return "BMF";
    if (team === "Crave Web") return "BMP";
    return null;
}
