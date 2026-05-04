import { describe, expect, it } from "vitest";
import { plantImageSrc } from "./plantMediaUrl";

describe("plantImageSrc", () => {
  it("returns root-relative URLs unchanged", () => {
    expect(plantImageSrc("/images/plants/1/x.png")).toBe("/images/plants/1/x.png");
  });

  it("strips host for /images/ absolute URLs", () => {
    expect(plantImageSrc("http://192.168.0.10:8080/images/plants/1/x.png")).toBe(
      "/images/plants/1/x.png"
    );
    expect(plantImageSrc("http://192.168.1.10:3000/images/plants/1/x.png")).toBe(
      "/images/plants/1/x.png"
    );
  });

  it("preserves query string on /images/ paths", () => {
    expect(plantImageSrc("http://10.0.0.5:8080/images/a.jpg?v=1")).toBe("/images/a.jpg?v=1");
  });

  it("does not rewrite S3 or external https URLs", () => {
    const s3 = "https://bucket.s3.us-east-1.amazonaws.com/plants/1/x.png";
    expect(plantImageSrc(s3)).toBe(s3);
    const inat = "https://www.inaturalist.org/photos/123";
    expect(plantImageSrc(inat)).toBe(inat);
  });

  it("handles null and empty", () => {
    expect(plantImageSrc(null)).toBe("");
    expect(plantImageSrc(undefined)).toBe("");
    expect(plantImageSrc("")).toBe("");
  });
});
