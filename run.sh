sbt stage

./modules/core/.jvm/target/universal/stage/bin/scala-steward \
  --workspace  "./workspace" \
  --repos-file "./repos.md" \
  --default-repo-conf "./default.scala-steward.conf" \
  --git-author-email dan@emagineers.co.uk \
  --vcs-api-host "https://api.github.com" \
  --vcs-login "danhamilton" \
  --git-ask-pass "./.gitcred" \
