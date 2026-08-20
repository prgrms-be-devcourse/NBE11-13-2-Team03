import { SharedArray } from "k6/data";

export const actors = new SharedArray("gudit performance actors", () => {
  const fixtures = JSON.parse(
    open("../../test-data/generated/performance-test-data.json")
  );
  return fixtures.actors;
});

export function actorForUser(userId) {
  const actor = actors[userId - 1];
  if (!actor || actor.userId !== userId) {
    throw new Error(`No performance-test actor found for userId=${userId}`);
  }
  return actor;
}
