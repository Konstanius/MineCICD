<hr style="border: 1px solid green;">
<h2 style="color:green">MineCICD 2.0 has been released!</h2>
<h3 style="color:green">Version 2.0 is a complete rewrite of the plugin, focussed on:</h3>
<li style="color:green">Improving performance of all operations</li>
<li style="color:green">Expanding compatibility to MS Windows and across versions</li>
<li style="color:green">Fixing bugs and making operations more reliable</li>
<li style="color:green">Adding new, suggested features</li>
<h3 style="color:green">Version 2.0 Changelog:</h3>
<li style="color:green">Git repository root is now the same as the server root</li>
<li style="color:green">Compatibility for Microsoft Windows added (no more file path issues)</li>
<li style="color:green">Removed whitelisting / blacklisting in the config, now done with .gitignore file</li>
<li style="color:green">Fixed most issues listed on GitHub</li>
<hr style="border: 1px solid green;">
<h2 style="color:red">Updating from MineCICD 1.* to 2.0 will reset the config! See Migration steps below</h2>

# MineCICD 2.1
#### Minecraft Versions [Paper / Spigot] 1.8.* - 1.21.*
## Continuous Integration, Continuous Delivery - Now for Minecraft Servers

### What is MineCICD?
MineCICD is a tool used for Minecraft Server and Network development, which can massively speed up all
setup- and configuration processes. It tracks all changes with Version Control System Integration, allows for
fast and safe reverts, rollbacks, insight into changes / bug tracking and much more.

Developers can use their personal IDE to edit files on their own Machine, push changes to the Repository, which
automatically applies the changes on the server and performs arbitrarily defined actions / commands / scripts,
with support for server shell commands.

Networks may make use of MineCICD to manage multiple servers, plugins, and configurations across all servers
simultaneously, with the ability to track changes and apply them to all servers at once!

## Installation & Setup
1. Download the plugin from the latest release on GitHub (https://github.com/Konstanius/MineCICD/releases)
2. Add the plugin into your plugins folder
3. Restart the Minecraft server
4. Create a Git repository (For example on GitHub at https://github.com/new or similar)
5. Link the repository and your access credentials in the config
    - For GitHub, you must use a Personal Access Token
      - Get the token from https://github.com/settings/tokens
      - It must have full repo permissions
      - Set it in the config.yml under both `git.user` and `git.pass`
    - Other Git providers may require different credentials
      - Set them in the config.yml under `git.user` and `git.pass` accordingly
6. Reload the config with `/minecicd reload`
7. Load the repository with `/minecicd pull`

### Now you are all set! - Time to start syncing
- Add files to Git Tracking with `/minecicd add <file>`
- Load changes made to the repository with `/minecicd pull`
- (Optionally) push changes from your server to the repository with `/minecicd push <commit message>`

### Which files should be tracked?
- All files that are part of the server setup and configuration should be tracked
- This includes plugins, server configurations, scripts, and other files that are part of the server setup
- **You should NOT track player data, world data, or other files that are generated and updated dynamically**
   - **Tracking world or player data will inevitably lead to conflicts and issues**

### Instant commit propagation setup
1. Create a webhook for your repository (Repo settings -> Webhooks -> Add webhook)
    - Payload URL: `http://<your server ip>:8080/minecicd` (Port / Path configurable in `config.yml`)
    - Content type: `application/json`
    - Set it to trigger only for `The push event`
2. You're all set! Now you can also use commit actions

### Commit Actions
Commit Actions are actions that are performed when a commit is pushed to the repository.<br>
They can range from restarting / reloading the server or individual plugins to executing game commands or
defined scripts containing game commands or shell commands.<br>
You can define commit actions as follows:
```yaml
# Execution order is top to bottom for commands, scripts and restart / reload
# Only one of restart / global-reload / reload (plugin(s)) is performed.
<any other commit message>
CICD restart (Will only stop the server, starting relies on your restart script / server host)
CICD global-reload (Reload the entire server using the reload command)
CICD reload <plugin-name> (Multiple plugins via separate lines can be specified (Requires PlugManX))
CICD run <command> (Multiple commands via separate lines can be specified)
CICD script <script-name> (Multiple scripts via separate lines can be specified)
<...>
```

### Secrets
Secrets are a way of storing sensitive information, such as passwords or API keys, in a dedicated, untracked file.<br>
They are defined in the `/secrets.yml` directory, following the following format:
```yaml
# secrets.yml
# Initial key is irrelevant, but must be unique
1:
  # File path is relative to the server root
  file: "plugins/example-plugin-1/config.yml"
  # Key-value pairs to replace in the file
  database_password: "password"
  database_username: "username"
2:
  file: "plugins/example-plugin-2/config.yml"
  license_key: "license_key"
```
After modifying this file, make sure to reload the plugin with `/minecicd reload` to apply the changes.<br>
These secrets will never be visible in the repository, but will be only be contained in the local server files.<br>
Since Windows does not come with the `sed` command, MineCICD ships with a custom implementation for Windows: `minecicd_tools/windows-replace.exe`.<br>
If your Linux installation does not have `sed`, MineCICD will use another custom implementation: `minecicd_tools/linux-replace.exe`.<br>

### Scripts
Scripts are a way of storing procedures of Minecraft commands and system shell commands.<br>
They are defined in the `plugins/MineCICD/scripts` directory as `<script_name>.sh` files.<br>
For a detailed description about the syntax, see the `plugins/MineCICD/scripts/example_script.sh` file.

### Version Tracking for jar files
Tracking plugin jarfiles with Git is currently only experimentally supported and can cause issues.<br>
This is due to issues with File Locks and the way Java handles jar files, but is being worked on.<br>
This feature is disabled by default, to enable it, do the following:
- Remove the `*.jar` line from the `.gitignore` file in the server root
- Install PlugManX on your server (https://www.spigotmc.org/resources/plugmanx.88135/)
- Enable the `experimental-jar-loading` option in the config.yml and reload MineCICD (`/minecicd reload`)

This will unload and load plugins if their jarfiles change, are removed. or new ones are added

## Commands
- `minecicd pull` - Pulls the latest changes from the remote or sets up the local repository if run for the first time.
- `minecicd push <commit message>` - Pushes the latest changes to the remote.
- `minecicd add <file / 'directory/'>` - Adds a file or directory to the repository.
- `minecicd remove <file / 'directory/'>` - Removes a file or directory from the repository.
- `minecicd reset <commit hash / link>` - Hard resets the current branch to the specified commit. (Commits will not be reverted)
- `minecicd rollback <dd.MM.yyyy HH:mm:ss>` - Hard resets the current branch to the latest commit before the specified date. (Commits will not be reverted)
- `minecicd revert <commit hash / link>` - Attempts to revert a specific commits changes.
- `minecicd script <script name>` - Runs a script from the scripts folder.
- `minecicd log <page / commit hash / link>` - Shows the commits made on the current branch.
- `minecicd status` - Shows the current status of the plugin, repository, Webhook listener, and changes.
- `minecicd resolve <merge-abort / repo-reset / reset-local-changes>` - Resolves conflicts by either aborting the merge, resetting the repository, or removing local changes.
- `minecicd diff <local / remote>` - Shows uncommited changes (local) or unpulled changes on the remote branch (remote).
- `minecicd reload` - Reloads the plugin configuration and webhook webserver.
- `minecicd help` - Shows this help message.

## Permissions
- `minecicd.<subcommand>` - Allows the user to use the subcommand
- `minecicd.notify` - Allows the user to receive notifications from actions performed by MineCICD

## Migrating from MineCICD 1.* to 2.0
1. Push any changes made on your server to the repository!
2. Install the new version of MineCICD
3. Copy the existing token from the old config and save them somewhere
4. The entire plugin directory of MineCICD will reset (No server files are affected)
5. Set up the new config with the token, repository url, branch name and other settings
6. Run `/minecicd pull` to clone the repository
  - MineCICD should automatically detect which files were added to the repository
  - If not, manually add them with `/minecicd add <file / 'directory/'>`