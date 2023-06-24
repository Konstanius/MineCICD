# MineCICD
## A Developers wet dream for Minecraft Server Projects

### What is MineCICD?
MineCICD is a tool used for Minecraft Server and Network development, which can massively speed up all
setup- and configuration processes. It tracks all changes with Version Control System Integration, allows for
fast and safe reverts, and rollbacks.

Developers can use their personal IDE to edit files on their own Machine, push changes to the Repository, which
automatically applies the changes on the server and performs arbitrarily defined actions / commands / scripts,
with support for machine shell commands.

## Installation & Setup
1. Drag and Drop the plugin into your plugins folder
2. Restart your server
3. Create a GitHub repository (https://github.com/new)
4. Link the repository and your Personal Access Token in the config
    - Get your Personal Access Token from https://github.com/settings/tokens
    - Token must have full repo permissions
5. Reload the config with `/minecicd reload`
6. Load the repository with `/minecicd clone`

### Now you are all set! - Time to start syncing
- Add files to Git Tracking with `/minecicd add <file> <commit message>`
- Load changes made to the repository with `/minecicd pull`

<hr style="border: 1px solid red;">
<h3 style="color:red">WARNING! Live Synchronisation is currently in Beta and not tested on release builds. Use at your own risk!</h3>
<hr style="border: 1px solid red;">

### Live Synchronisation Setup
1. Create a webhook for your repository (Repo settings -> Webhooks -> Add webhook)
   - Payload URL: `http://<your server ip>:8080/minecicd` (Port / Path configurable in config.yml)
   - Content type: application/json
   - Just the push event
2. You're all set! Now you can also use commit actions

#### Commit Actions
Commit Actions are actions that are performed when a commit is pushed to the repository.

They can range from restarting / reloading the server or individual plugins to executing game commands or
defined scripts containing game commands or shell commands.

You can define commit actions as follows:
```yaml
# Execution order is top to bottom for commands, scripts and restart / reload
# Only one of restart / global-reload / reload (plugin(s)) is performed.
CICD <restart>
CICD <global-reload>
CICD <reload> <plugin-name> (Multiple plugins can be specified)
CICD <run> <command> (Multiple commands can be specified)
CICD <script> <script-name> (Multiple scripts can be specified)
<any other commit message>
```

###### End of Live Synchronisation Setup

## Commands
- `minecicd clone` - Clones the repo from the remote
- `minecicd pull` - Pulls the repo from the remote
- `minecicd push <commit message>` - Pushes the repo to the remote
- `minecicd add <file> <message>` - Adds a file to the repo
- `minecicd remove <file> <message>` - Removes a file from the repo
- `minecicd reset <commit hash / link>` - Resets the current branch to a specific commit
- `minecicd revert <commit hash / link>` - Reverts a specific commit
- `minecicd script <script name>` - Runs a script from MineCICD/scripts/(script name).sh
- `minecicd log <page>` - Lists the commits in the repo
- `minecicd status` - Gets the status of the repo
- `minecicd mute <true / false>` - Mutes MineCICD messages
- `minecicd reload` - Reloads the config
- `minecicd help` - Shows the help message

## Permissions
- `minecicd.<subcommand>` - Allows the user to use the subcommand
- `minecicd.notify` - Allows the user to receive notifications from actions performed by MineCICD
