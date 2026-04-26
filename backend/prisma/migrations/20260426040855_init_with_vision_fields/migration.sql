-- CreateEnum
CREATE TYPE "UserRole" AS ENUM ('MINOR', 'TRUSTED_ADULT', 'AUTHORITY', 'ADMIN');

-- CreateEnum
CREATE TYPE "RiskLevel" AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');

-- CreateEnum
CREATE TYPE "EventSource" AS ENUM ('OCR_EMOJI', 'VISION', 'NLP', 'MANUAL');

-- CreateEnum
CREATE TYPE "AlertStatus" AS ENUM ('PENDING', 'SEEN', 'RESOLVED');

-- CreateEnum
CREATE TYPE "ReportStatus" AS ENUM ('SUBMITTED', 'IN_REVIEW', 'CLOSED');

-- CreateTable
CREATE TABLE "User" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "email" TEXT,
    "phone" TEXT,
    "role" "UserRole" NOT NULL,
    "locationState" TEXT,
    "locationCity" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "User_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "MinorProfile" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "trustCode" TEXT NOT NULL,
    "deviceId" TEXT,
    "guardianNotes" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "MinorProfile_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "TrustedAdultProfile" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "relationship" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "TrustedAdultProfile_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "TrustLink" (
    "id" TEXT NOT NULL,
    "minorProfileId" TEXT NOT NULL,
    "adultProfileId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "TrustLink_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AiEvent" (
    "id" TEXT NOT NULL,
    "minorId" TEXT NOT NULL,
    "source" "EventSource" NOT NULL,
    "platform" TEXT NOT NULL,
    "riskType" TEXT NOT NULL,
    "riskLevel" "RiskLevel" NOT NULL,
    "summary" TEXT NOT NULL,
    "rawText" TEXT,
    "emojiTags" TEXT[],
    "evidenceUrl" TEXT,
    "locationState" TEXT,
    "locationCity" TEXT,
    "score" DOUBLE PRECISION,
    "visionLabel" TEXT,
    "visionObjects" TEXT[],
    "detectedUser" TEXT,
    "screenContext" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AiEvent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Alert" (
    "id" TEXT NOT NULL,
    "aiEventId" TEXT NOT NULL,
    "adultId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "message" TEXT NOT NULL,
    "status" "AlertStatus" NOT NULL DEFAULT 'PENDING',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Alert_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SafetyReport" (
    "id" TEXT NOT NULL,
    "aiEventId" TEXT NOT NULL,
    "createdById" TEXT NOT NULL,
    "authorityArea" TEXT,
    "description" TEXT NOT NULL,
    "status" "ReportStatus" NOT NULL DEFAULT 'SUBMITTED',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "SafetyReport_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "User_email_key" ON "User"("email");

-- CreateIndex
CREATE UNIQUE INDEX "MinorProfile_userId_key" ON "MinorProfile"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "MinorProfile_trustCode_key" ON "MinorProfile"("trustCode");

-- CreateIndex
CREATE UNIQUE INDEX "MinorProfile_deviceId_key" ON "MinorProfile"("deviceId");

-- CreateIndex
CREATE UNIQUE INDEX "TrustedAdultProfile_userId_key" ON "TrustedAdultProfile"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "TrustLink_minorProfileId_adultProfileId_key" ON "TrustLink"("minorProfileId", "adultProfileId");

-- AddForeignKey
ALTER TABLE "MinorProfile" ADD CONSTRAINT "MinorProfile_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TrustedAdultProfile" ADD CONSTRAINT "TrustedAdultProfile_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TrustLink" ADD CONSTRAINT "TrustLink_adultProfileId_fkey" FOREIGN KEY ("adultProfileId") REFERENCES "TrustedAdultProfile"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TrustLink" ADD CONSTRAINT "TrustLink_minorProfileId_fkey" FOREIGN KEY ("minorProfileId") REFERENCES "MinorProfile"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AiEvent" ADD CONSTRAINT "AiEvent_minorId_fkey" FOREIGN KEY ("minorId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Alert" ADD CONSTRAINT "Alert_adultId_fkey" FOREIGN KEY ("adultId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Alert" ADD CONSTRAINT "Alert_aiEventId_fkey" FOREIGN KEY ("aiEventId") REFERENCES "AiEvent"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SafetyReport" ADD CONSTRAINT "SafetyReport_aiEventId_fkey" FOREIGN KEY ("aiEventId") REFERENCES "AiEvent"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SafetyReport" ADD CONSTRAINT "SafetyReport_createdById_fkey" FOREIGN KEY ("createdById") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
