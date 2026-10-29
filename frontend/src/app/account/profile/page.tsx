import type { Metadata } from "next";

import { AddressBook, ConsentSettings, ProfileForm } from "@/components/account/account-settings";

export const metadata: Metadata = { title: "Profile" };

export default function ProfilePage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Profile &amp; addresses</h1>
      <ProfileForm />
      <AddressBook />
      <ConsentSettings />
    </div>
  );
}
