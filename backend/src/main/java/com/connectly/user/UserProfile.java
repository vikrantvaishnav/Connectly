package com.connectly.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Column(length = 500)
    private String bio;

    /** App-relative media URL (e.g. /media/abc.jpg) — uploads go through MediaService. */
    @Column(name = "profile_image", length = 500)
    private String profileImage;

    /** Comma-separated interest tags, e.g. "coffee, hiking, techno". */
    @Column(length = 300)
    private String interests;

    /** Short intent line shown on discovery cards, e.g. "Coffee & good conversation". */
    @Column(name = "looking_for", length = 60)
    private String lookingFor;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }
    public String getInterests() { return interests; }
    public void setInterests(String interests) { this.interests = interests; }
    public String getLookingFor() { return lookingFor; }
    public void setLookingFor(String lookingFor) { this.lookingFor = lookingFor; }

    // referenced by migration for date_of_birth/profession columns; not exposed yet
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 120)
    private String profession;

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }
}
